package at.koopro.wizardsandbeasts.admin.config.catalog;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Which {@code Config} keys the Control Center exposes, where each one is filed, and who reads it.
 *
 * <p>Deliberately metadata only — no type, no range, no default. Those are read from the
 * {@code ModConfigSpec} entry by {@link ConfigSettingBinder} at bind time, so a range edited in
 * {@code Config.java} is the range the admin panel enforces, with no second copy to update. This class never
 * names {@code Config}, which keeps it loadable in a plain unit test.
 *
 * <p><b>Scope is decided by who reads the value, not by where it is declared.</b> Every key below lives in
 * the one COMMON config, but:
 * <ul>
 *   <li>the HUD, screen-effect, wind and FOV keys are read by client renderers;</li>
 *   <li>{@code broomSpeedMultiplier} and {@code broomGentleLanding} are read by {@code BroomMovement} /
 *       {@code BroomImpacts}, which run on the rider's client because broom flight is client-authoritative
 *       ({@code BroomEntity#tick}).</li>
 * </ul>
 * A server-side write to any of those would report success and change nothing for a player on a dedicated
 * server, so they are {@link SettingScope#CLIENT}: listed, explained, never writable. Server-driven
 * replication of such values is the Phase-2 answer.
 *
 * <p>To expose another key: add an {@link Entry}, then add its three lang keys
 * ({@code admin.wizards_and_beasts.setting.<path>}, {@code .desc}, and {@code .warning} when dangerous).
 * {@code AdminCatalogLangTest} fails until the keys exist.
 */
@NullMarked
public final class ConfigSettingCatalog {

    /**
     * @param path      the admin id path (namespace is the mod's); snake_case, stable, typed in commands
     * @param configKey the {@code ModConfigSpec} key in {@code Config}
     * @param danger    which changes need an explicit confirmation (enforced by the server)
     * @param rule      a cross-setting rule the binder attaches, or null
     */
    public record Entry(String path, String configKey, AdminCategory category, SettingScope scope,
                        Danger danger, @Nullable Rule rule) {

        /** Whether any change of this setting can need confirmation, i.e. it needs a {@code .warning} key. */
        public boolean dangerous() {
            return danger != Danger.NONE;
        }
    }

    /**
     * Which changes of a setting are dangerous. Named here; the binder turns each into an
     * {@code AdminSetting.DangerRule} over the setting's real type.
     */
    public enum Danger {
        NONE,
        /** Every change. */
        ALWAYS,
        /** A switch turned on (enabling the Unforgivables). */
        WHEN_TURNED_ON,
        /** A switch turned off (disabling prerequisites). */
        WHEN_TURNED_OFF,
        /** A number raised above twice its default ("massively increasing damage"). */
        ABOVE_DOUBLE_DEFAULT,
        /** A number cut below half its default (cooldowns that let spells be spammed). */
        BELOW_HALF_DEFAULT
    }

    /** Cross-setting rules. Named here, implemented in the binder where the types are known. */
    public enum Rule {
        /** The five spell-power bounds must form a valid {@code SpellPower.Bounds}. */
        SPELL_POWER_BOUNDS
    }

    public static final List<Entry> ENTRIES = List.of(
            // ── Game rules: how the game plays for everyone ──
            // Moved to Economy in Phase 8: it is a price, charged from the vault.
            server("skill_respec_cost_knuts", "skillRespecCostKnuts", AdminCategory.ECONOMY, Danger.NONE),
            server("skill_unlearn_cost_knuts_per_point", "skillUnlearnCostKnutsPerPoint", AdminCategory.ECONOMY, Danger.NONE),

            // ── Magic → Rules: server-wide spell rules ──
            // Turning requirements off lets anyone cast any spell they know, whatever its prerequisite.
            server("enforce_spell_requirements", "enforceSpellRequirements", AdminCategory.MAGIC, Danger.WHEN_TURNED_OFF),
            server("spell_damage_multiplier", "spellDamageMultiplier", AdminCategory.MAGIC, Danger.ABOVE_DOUBLE_DEFAULT),
            server("spell_cooldown_multiplier", "spellCooldownMultiplier", AdminCategory.MAGIC, Danger.BELOW_HALF_DEFAULT),
            server("spell_range_multiplier", "spellRangeMultiplier", AdminCategory.MAGIC, Danger.ABOVE_DOUBLE_DEFAULT),
            server("spell_block_damage", "spellBlockDamage", AdminCategory.MAGIC, Danger.NONE),
            spellPower("spell_power_soft_cap_knee", "spellPowerSoftCapKnee"),
            spellPower("spell_power_max_multiplier", "spellPowerMaxMultiplier"),
            spellPower("spell_power_min_multiplier", "spellPowerMinMultiplier"),
            spellPower("spell_cooldown_min_multiplier", "spellCooldownMinMultiplier"),
            spellPower("spell_cooldown_max_multiplier", "spellCooldownMaxMultiplier"),

            // ── Dark Arts ──
            server("allow_unforgivable_curses", "allowUnforgivableCurses", AdminCategory.DARK_ARTS, Danger.WHEN_TURNED_ON),

            // ── Wands → Rules: the allegiance system's switch and the numbers its rules read ──
            server("enable_wand_allegiance", "enableWandAllegiance", AdminCategory.WANDS, Danger.ALWAYS),
            server("wand_affinity_strength", "wandAffinityStrength", AdminCategory.WANDS, Danger.NONE),
            server("wand_bond_growth_multiplier", "wandBondGrowthMultiplier", AdminCategory.WANDS, Danger.ABOVE_DOUBLE_DEFAULT),
            server("wand_defeats_to_win", "wandDefeatsToWin", AdminCategory.WANDS, Danger.NONE),
            server("wand_neglect_loss_multiplier", "wandNeglectLossMultiplier", AdminCategory.WANDS, Danger.NONE),
            server("wand_foreign_backfire", "wandForeignBackfire", AdminCategory.WANDS, Danger.NONE),

            // ── Brewing → Rules: the cauldron's own decision points ──
            // Switching brewing off stops every new brew on the server.
            server("brewing_enabled", "brewingEnabled", AdminCategory.BREWING, Danger.WHEN_TURNED_OFF),
            server("brew_speed_multiplier", "brewSpeedMultiplier", AdminCategory.BREWING, Danger.ABOVE_DOUBLE_DEFAULT),
            server("brew_failure_multiplier", "brewFailureMultiplier", AdminCategory.BREWING, Danger.NONE),
            // Lets any cauldron brew with nothing under it: the heat-source economy stops mattering.
            server("brew_require_heat_source", "brewRequireHeatSource", AdminCategory.BREWING, Danger.WHEN_TURNED_OFF),
            server("brew_contamination_penalty", "brewContaminationPenalty", AdminCategory.BREWING, Danger.NONE),

            // ── Creatures → Rules: which beasts appear on their own (the per-creature switch is a creature rule) ──
            server("creature_natural_spawns", "creatureNaturalSpawns", AdminCategory.CREATURES, Danger.NONE),

            // ── Heritages: the rules the heritage systems already read ──
            server("werewolf_forced_transform", "werewolfEnableForcedTransform", AdminCategory.HERITAGES, Danger.NONE),
            server("werewolf_loss_of_control", "werewolfLossOfControl", AdminCategory.HERITAGES, Danger.NONE),
            // Spreads a heritage condition from player to player.
            server("werewolf_bite_infects", "werewolfBiteInfects", AdminCategory.HERITAGES, Danger.WHEN_TURNED_ON),
            server("werewolf_wolfsbane_suppresses_transform", "werewolfWolfsbaneSuppressesTransform",
                    AdminCategory.HERITAGES, Danger.NONE),
            // The two heritage PvP rules that exist: a feral wolf turning on other werewolves, a vampire feeding on
            // players. Both turn a heritage into a way to attack players who did not opt into PvP.
            server("werewolf_pack_betrayal", "werewolfPackBetrayal", AdminCategory.HERITAGES, Danger.WHEN_TURNED_ON),
            server("vampire_feed_on_players", "vampireBloodAllowFeedingOnPlayers", AdminCategory.HERITAGES,
                    Danger.WHEN_TURNED_ON),

            // ── Travel: read by the rider's own game ──
            client("broom_speed_multiplier", "broomSpeedMultiplier", AdminCategory.TRAVEL),
            // The Floo Network: every value has one reader on the server (FlooTravelHandler, FlooDeparture,
            // FlooFireplaceBlockEntity, FlooFlamesBlock, FlooAccess). The registration fee is filed under Economy.
            server("floo_travel_cooldown_ticks", "flooTravelCooldownTicks", AdminCategory.TRAVEL, Danger.NONE),
            server("floo_misfire_chance_percent", "flooMisfireChancePercent", AdminCategory.TRAVEL, Danger.NONE),
            server("floo_departure_windup_ticks", "flooDepartureWindupTicks", AdminCategory.TRAVEL, Danger.NONE),
            server("floo_lit_timeout_ticks", "flooLitTimeoutTicks", AdminCategory.TRAVEL, Danger.NONE),
            server("floo_ticks_per_charge", "flooTicksPerCharge", AdminCategory.TRAVEL, Danger.NONE),
            server("floo_speak_typo_tolerance", "flooSpeakTypoTolerance", AdminCategory.TRAVEL, Danger.NONE),
            server("floo_fuzzy_match", "flooFuzzyMatch", AdminCategory.TRAVEL, Danger.NONE),
            client("broom_gentle_landing", "broomGentleLanding", AdminCategory.TRAVEL),
            // The server's side of broom speed: a scale sent in the definitions, and the check on what riders fly.
            server("broom_server_speed_scale", "broomServerSpeedScale", AdminCategory.TRAVEL, Danger.NONE),
            server("broom_speed_guard", "broomSpeedGuard", AdminCategory.TRAVEL, Danger.WHEN_TURNED_OFF),

            // ── Ministry ──
            server("ministry_fine_scale_percent", "ministryFineScalePercent", AdminCategory.MINISTRY, Danger.NONE),
            // How likely a fence's deal is to reach the Ministry; 0 makes the black market a risk-free shop.
            server("illicit_detection_scale_percent", "illicitDetectionScalePercent", AdminCategory.MINISTRY,
                    Danger.WHEN_TURNED_OFF),
            // Moves every underage character's coming of age at once.
            server("ministry_days_per_year", "ministryDaysPerYear", AdminCategory.MINISTRY, Danger.ALWAYS),
            // New characters only: an existing character's age is never touched (MinistryTraceEvents).
            server("ministry_new_character_age", "ministryNewCharacterAge", AdminCategory.MINISTRY, Danger.NONE),
            server("ministry_notoriety_decay_per_second", "ministryNotorietyDecayPerSecond", AdminCategory.MINISTRY,
                    Danger.NONE),
            // 0 takes away the only remedy for a contested ruling that only just met the bar.
            server("ministry_appeal_window_days", "ministryAppealWindowDays", AdminCategory.MINISTRY,
                    Danger.WHEN_TURNED_OFF),
            server("ministry_appeal_fee_knuts", "ministryAppealFeeKnuts", AdminCategory.MINISTRY, Danger.NONE),
            server("ministry_licence_fee_knuts", "ministryLicenceFeeKnuts", AdminCategory.MINISTRY, Danger.NONE),
            server("ministry_licence_term_days", "ministryLicenceTermDays", AdminCategory.MINISTRY, Danger.NONE),
            server("ministry_unlicensed_sales_knuts_per_week", "ministryUnlicensedSalesKnutsPerWeek",
                    AdminCategory.MINISTRY, Danger.NONE),
            // Off, players with the Auror profession lose their casework; the Ministry's own Aurors carry on.
            server("ministry_player_aurors", "ministryPlayerAurors", AdminCategory.MINISTRY, Danger.WHEN_TURNED_OFF),
            server("ministry_npc_cases", "ministryNpcCases", AdminCategory.MINISTRY, Danger.NONE),
            server("ministry_bribery", "ministryBribery", AdminCategory.MINISTRY, Danger.NONE),

            // ── Economy: prices and fees. None of these creates money; each is charged at one read point. ──
            server("ollivander_wand_price_knuts", "ollivanderWandPriceKnuts", AdminCategory.ECONOMY, Danger.NONE),
            server("wand_care_price_knuts", "wandCarePriceKnuts", AdminCategory.ECONOMY, Danger.NONE),
            server("licence_endorsement_fee_knuts", "licenceEndorsementFeeKnuts", AdminCategory.ECONOMY, Danger.NONE),
            server("floo_registration_fee_knuts", "flooRegistrationFeeKnuts", AdminCategory.ECONOMY, Danger.NONE),
            server("dragot_galleon_rate", "dragotGalleonRate", AdminCategory.ECONOMY, Danger.NONE),
            // Markets (market/): scales over every trader's book; supply and demand still move each good.
            server("market_price_scale_percent", "marketPriceScalePercent", AdminCategory.ECONOMY, Danger.NONE),
            server("market_recovery_scale_percent", "marketRecoveryScalePercent", AdminCategory.ECONOMY, Danger.NONE),
            server("exploration_fee_scale_percent", "explorationFeeScalePercent", AdminCategory.ECONOMY, Danger.NONE),

            // ── World: hostile-spawn suppression around duelling dummies (ScarecrowSpawnGate, server) ──
            server("dummy_scarecrow", "dummyScarecrow", AdminCategory.WORLD, Danger.NONE),
            server("dummy_scare_radius", "dummyScareRadius", AdminCategory.WORLD, Danger.NONE),

            // ── Visuals: each player's own preference ──
            client("show_spell_hud_overlay", "showSpellHudOverlay", AdminCategory.VISUALS),
            client("reduce_screen_effects", "reduceScreenEffects", AdminCategory.VISUALS),
            client("broom_wind_volume", "broomWindVolume", AdminCategory.VISUALS),
            client("broom_fov_effect", "broomFovEffect", AdminCategory.VISUALS),
            client("broom_speed_particles", "broomSpeedParticles", AdminCategory.VISUALS),

            // ── Performance ──
            server("perf_profile", "perfProfile", AdminCategory.PERFORMANCE, Danger.NONE),
            server("beam_target_scan_interval_ticks", "beamTargetScanIntervalTicks", AdminCategory.PERFORMANCE, Danger.NONE),
            server("beam_channel_effect_interval_ticks", "beamChannelEffectIntervalTicks", AdminCategory.PERFORMANCE, Danger.NONE),

            // ── Debug ──
            // Dangerous because DebugAutoGrantSource hands the test abilities to every player while it is on.
            server("enable_debug_tools", "enableDebugTools", AdminCategory.DEBUG, Danger.WHEN_TURNED_ON),
            server("debug_log_spell_gate_reasons", "debugLogSpellGateReasons", AdminCategory.DEBUG, Danger.NONE));

    private ConfigSettingCatalog() {}

    private static Entry server(String path, String key, AdminCategory category, Danger danger) {
        return new Entry(path, key, category, SettingScope.SERVER, danger, null);
    }

    private static Entry client(String path, String key, AdminCategory category) {
        return new Entry(path, key, category, SettingScope.CLIENT, Danger.NONE, null);
    }

    /** Spell power moves damage and cooldowns for every caster, so every bound asks for confirmation. */
    private static Entry spellPower(String path, String key) {
        return new Entry(path, key, AdminCategory.MAGIC, SettingScope.SERVER, Danger.ALWAYS, Rule.SPELL_POWER_BOUNDS);
    }
}
