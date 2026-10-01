package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.debug.CastDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.DebugLeases;
import at.koopro.wizardsandbeasts.admin.debug.DebugLeases.Tool;
import at.koopro.wizardsandbeasts.admin.debug.LiveDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.ModLogLevel;
import at.koopro.wizardsandbeasts.admin.perf.PerformanceMetrics;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets;
import at.koopro.wizardsandbeasts.admin.profile.ProfileApplier;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.command.debug.DebugModeService;
import at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Performance and Debug against the live server: the metrics are the game's own numbers, presets change exactly the
 * settings they name, debug tools switched on from the panel go back when the lease ends (release, logout, expiry), the
 * log level never leaves this mod's loggers, and the live view shows real cast events and sessions.
 */
public final class AdminOpsTests {

    private static final AdminContext CONSOLE = AdminContext.detached(null, "game-test", EnumSet.allOf(AdminCapability.class));

    private AdminOpsTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("admin_ops_metrics_are_measured",
                "admin ops: entity counts, players, beams and sessions match the game; tick figures are bounded and real",
                AdminOpsTests::metricsAreMeasured);
        // Presets rewrite three server-wide settings other scenarios read (beam intervals): alone.
        tests.addAlone("admin_ops_presets_apply_the_settings",
                "admin ops: a preset sets exactly its settings through the service; CUSTOM when they match none",
                AdminOpsTests::presetsApply);
        // Debug mode for everyone and the log level are server-wide too: alone.
        tests.addAlone("admin_ops_debug_tools_are_leased",
                "admin ops: debug tools go back on release, logout and expiry; shared tools wait for the last holder",
                AdminOpsTests::debugToolsAreLeased);
        tests.addAlone("admin_ops_log_level_stays_in_the_mod",
                "admin ops: the log level moves only this mod's logger and is put back exactly, entry and all",
                AdminOpsTests::logLevelStaysInTheMod);
        tests.add("admin_ops_live_diagnostics_show_casts_and_sessions",
                "admin ops: cast events and open wand holds appear in the live view; reads are throttled and gated",
                AdminOpsTests::liveDiagnostics);
    }

    private static AdminContext admin(UUID id, String name) {
        return AdminContext.detached(id, name, EnumSet.allOf(AdminCapability.class));
    }

    // ── metrics ──

    private static void metricsAreMeasured(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "OpsMetrics", GameType.SURVIVAL);
        List<Entity> spawned = new ArrayList<>();
        try {
            PerformanceMetrics.Snapshot before = PerformanceMetrics.collect(server);
            for (int i = 0; i < 3; i++) {
                spawned.add(helper.spawn(ModEntities.DUELLING_DUMMY.get(), new BlockPos(1 + i, 1, 1)));
            }
            PerformanceMetrics.Snapshot after = PerformanceMetrics.collect(server);
            check(helper, after.entities() - before.entities() == 3 && after.modEntities() - before.modEntities() == 3,
                    () -> "three spawned dummies counted as " + (after.entities() - before.entities()) + " entities / "
                            + (after.modEntities() - before.modEntities()) + " mod entities");
            check(helper, after.players() == server.getPlayerList().getPlayerCount() && after.networkMeasured(),
                    () -> "players " + after.players() + " vs " + server.getPlayerList().getPlayerCount());
            check(helper, after.beams() == WandBeamChannelLogic.channelCount()
                            && after.castSessions() == WandCastSessions.openSessions().size(),
                    () -> "beams/sessions disagree with the systems that hold them");
            check(helper, after.targetTps() == server.tickRateManager().tickrate(), () -> "target tps");
            if (after.tps() != PerformanceMetrics.UNAVAILABLE) {
                check(helper, after.tps() > 0 && after.tps() <= after.targetTps() && after.msptAvg() > 0
                                && after.msptMax() >= after.msptAvg(),
                        () -> "tick figures out of bounds: " + after.tps() + " tps, " + after.msptAvg() + "/" + after.msptMax());
            }
            check(helper, after.beamScanInterval() == WandBeamChannelLogic.scanIntervalTicks(), () -> "scan interval");
            helper.succeed();
        } finally {
            spawned.forEach(Entity::discard);
            WizardTestSupport.retire(helper, player);
        }
    }

    // ── presets ──

    private static void presetsApply(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PerformancePresets.Preset start = PerformancePresets.current(AdminSettings.service().registry());
        try {
            ProfileApplier.Outcome low = PerformancePresets.apply(AdminSettings.service(), CONSOLE,
                    PerformancePresets.Preset.LOW, server);
            check(helper, low.applied() && Config.perfProfile == Config.PerfProfile.LOW
                            && Config.beamTargetScanIntervalTicks == 4 && Config.beamChannelEffectIntervalTicks == 8,
                    () -> "LOW did not set its values: " + low);
            check(helper, PerformancePresets.current(AdminSettings.service().registry()) == PerformancePresets.Preset.LOW,
                    () -> "LOW not recognised after applying it");
            // The profile shifts the interval in effect: LOW adds 2 ticks to the setting.
            check(helper, WandBeamChannelLogic.scanIntervalTicks() == 6 && WandBeamChannelLogic.effectIntervalTicks() == 10,
                    () -> "intervals in effect " + WandBeamChannelLogic.scanIntervalTicks() + "/" + WandBeamChannelLogic.effectIntervalTicks());
            ProfileApplier.Outcome again = PerformancePresets.apply(AdminSettings.service(), CONSOLE,
                    PerformancePresets.Preset.LOW, server);
            check(helper, again.applied() && again.changed() == 0, () -> "re-applying LOW changed something: " + again);
            AdminSettings.service().change(CONSOLE, Identifier.fromNamespaceAndPath("wizards_and_beasts",
                    "beam_target_scan_interval_ticks"), "7", true);
            check(helper, PerformancePresets.current(AdminSettings.service().registry()) == null,
                    () -> "a hand-edited value still matched a preset (should be CUSTOM)");
            AdminContext nobody = AdminContext.detached(null, "no-config", EnumSet.noneOf(AdminCapability.class));
            ProfileApplier.Outcome refused = PerformancePresets.apply(AdminSettings.service(), nobody,
                    PerformancePresets.Preset.HIGH, server);
            check(helper, !refused.applied() && Config.beamTargetScanIntervalTicks == 7,
                    () -> "a preset applied without authority: " + refused);
            helper.succeed();
        } finally {
            PerformancePresets.apply(AdminSettings.service(), CONSOLE,
                    start == null ? PerformancePresets.Preset.MEDIUM : start, server);
        }
    }

    // ── leases ──

    private static void debugToolsAreLeased(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean globalBefore = DebugModeService.isGlobalEnabled();
        boolean spellBefore = DebugHooks.spellLoggingForAll();
        ServerPlayer leaver = WizardTestSupport.placeMockPlayer(helper, "OpsLeaver", GameType.SURVIVAL);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try {
            // Authority and who may hold a lease.
            AdminContext noDebug = AdminContext.detached(a, "no-debug", EnumSet.of(AdminCapability.CONFIG));
            check(helper, "unauthorized".equals(DebugLeases.set(noDebug, server, Tool.SPELL_LOGGING, "true").code())
                            && "panel_only".equals(DebugLeases.set(CONSOLE, server, Tool.SPELL_LOGGING, "true").code())
                            && "invalid_value".equals(DebugLeases.set(admin(a, "A"), server, Tool.SPELL_LOGGING, "maybe").code())
                            && DebugHooks.spellLoggingForAll() == spellBefore,
                    () -> "a refused switch changed something");

            // Own debug mode: on, then released.
            check(helper, DebugLeases.set(admin(a, "A"), server, Tool.MY_DEBUG_MODE, "true").success()
                    && DebugModeService.hasOwn(a) && DebugLeases.state(a).held().contains(Tool.MY_DEBUG_MODE), () -> "own debug on");
            DebugLeases.release(a, server);
            check(helper, !DebugModeService.hasOwn(a), () -> "own debug mode survived the release");

            // Shared tools wait for the last holder.
            DebugLeases.set(admin(a, "A"), server, Tool.SPELL_LOGGING, "true");
            DebugLeases.set(admin(b, "B"), server, Tool.SPELL_LOGGING, "true");
            DebugLeases.set(admin(a, "A"), server, Tool.ALL_DEBUG_MODE, "true");
            DebugLeases.release(a, server);
            check(helper, DebugHooks.spellLoggingForAll() && DebugModeService.isGlobalEnabled() == globalBefore,
                    () -> "releasing A dropped B's spell logging, or kept A's all-player debug");
            DebugLeases.release(b, server);
            check(helper, DebugHooks.spellLoggingForAll() == spellBefore, () -> "spell logging survived its last holder");

            // Logout releases.
            AdminContext leaving = admin(leaver.getUUID(), "OpsLeaver");
            DebugLeases.set(leaving, server, Tool.ALL_DEBUG_MODE, "true");
            check(helper, DebugModeService.isGlobalEnabled(), () -> "all-player debug did not turn on");
            WizardTestSupport.retire(helper, leaver);
            check(helper, DebugModeService.isGlobalEnabled() == globalBefore, () -> "a logged-out admin's debug mode stayed on");

            // Expiry releases a lease the panel stopped renewing.
            DebugLeases.set(admin(a, "A"), server, Tool.MY_DEBUG_MODE, "true");
            check(helper, DebugLeases.expire(System.currentTimeMillis() + DebugLeases.TTL_MILLIS + 1000, server) >= 1
                    && !DebugModeService.hasOwn(a), () -> "an expired lease kept debug mode on");

            // Turning a tool off needs no lease and leaves none.
            DebugLeases.set(admin(a, "A"), server, Tool.SPELL_LOGGING, "true");
            DebugLeases.set(admin(a, "A"), server, Tool.SPELL_LOGGING, "false");
            check(helper, DebugLeases.state(a).held().isEmpty() && DebugHooks.spellLoggingForAll() == spellBefore,
                    () -> "switching back off left a lease");
            helper.succeed();
        } finally {
            DebugLeases.release(a, server);
            DebugLeases.release(b, server);
            DebugModeService.setGlobal(globalBefore);
            DebugHooks.setSpellLoggingForAll(spellBefore);
            if (leaver.connection != null && server.getPlayerList().getPlayer(leaver.getUUID()) != null) {
                WizardTestSupport.retire(helper, leaver);
            }
        }
    }

    private static void logLevelStaysInTheMod(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID a = UUID.randomUUID();
        Level rootBefore = LogManager.getRootLogger().getLevel();
        boolean vanillaDebugBefore = LogManager.getLogger("net.minecraft.server.MinecraftServer").isDebugEnabled();
        ModLogLevel.Choice modBefore = ModLogLevel.current();
        boolean ownEntryBefore = DebugLeasesAccess.modHasOwnEntry();
        try {
            check(helper, DebugLeases.set(admin(a, "A"), server, Tool.LOG_LEVEL, "debug").success()
                    && ModLogLevel.current() == ModLogLevel.Choice.DEBUG
                    && LogManager.getLogger("at.koopro.wizardsandbeasts.gametest.Probe").isDebugEnabled(),
                    () -> "the mod's loggers did not go to DEBUG");
            check(helper, LogManager.getRootLogger().getLevel() == rootBefore
                            && LogManager.getLogger("net.minecraft.server.MinecraftServer").isDebugEnabled() == vanillaDebugBefore,
                    () -> "DEBUG leaked beyond the mod's loggers");
            DebugLeases.release(a, server);
            check(helper, ModLogLevel.current() == modBefore && DebugLeasesAccess.modHasOwnEntry() == ownEntryBefore,
                    () -> "the mod's log level was not put back exactly: " + ModLogLevel.current());
            check(helper, "invalid_value".equals(DebugLeases.set(admin(a, "A"), server, Tool.LOG_LEVEL, "loud").code()),
                    () -> "an unknown level was accepted");
            helper.succeed();
        } finally {
            DebugLeases.release(a, server);
        }
    }

    /** Whether the logging configuration has an entry of its own for the mod (rather than inheriting the root's). */
    private static final class DebugLeasesAccess {
        static boolean modHasOwnEntry() {
            org.apache.logging.log4j.core.LoggerContext context =
                    (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
            return context.getConfiguration().getLoggerConfig(ModLogLevel.LOGGER).getName().equals(ModLogLevel.LOGGER);
        }
    }

    // ── live diagnostics ──

    private static void liveDiagnostics(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "OpsCaster", GameType.SURVIVAL);
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "OpsOutsider", GameType.SURVIVAL);
        try {
            DebugHooks.logSpellCast(caster, "cast_reject", "gametest_reason");
            WandCastSessions.begin(caster, caster.level().getGameTime(), "wizards_and_beasts:lumos");
            LiveDiagnostics.Snapshot live = LiveDiagnostics.collect(server);
            check(helper, live.recentCasts().stream().anyMatch(e -> e.player().equals("OpsCaster")
                            && e.event().equals("cast_reject") && e.detail().equals("gametest_reason") && e.failure()),
                    () -> "the cast event is not in the live view");
            check(helper, live.castCounts().stream().anyMatch(c -> c.key().equals("cast_reject:gametest_reason")),
                    () -> "the refusal was not counted");
            check(helper, live.sessions().stream().anyMatch(s -> s.player().equals("OpsCaster")
                            && s.spell().equals("wizards_and_beasts:lumos") && !s.releaseConsumed()),
                    () -> "the open hold is not in the live view");
            check(helper, live.modules().size() == at.koopro.wizardsandbeasts.module.Module.values().length,
                    () -> "not every module is listed");
            check(helper, CastDiagnostics.recent(1000).size() <= CastDiagnostics.CAPACITY, () -> "the ring is unbounded");

            WizardTestSupport.drainClientboundPayloads(outsider);
            check(helper, !AdminOpsPayloads.sendDiagnostics(outsider, false) && !AdminOpsPayloads.sendMetrics(outsider),
                    () -> "a non-admin was sent diagnostics or metrics");
            check(helper, WizardTestSupport.drainClientboundPayloads(outsider).stream().noneMatch(p ->
                            p instanceof AdminOpsPayloads.DiagnosticsReply || p instanceof AdminOpsPayloads.MetricsReply),
                    () -> "a non-admin received an ops reply");
            helper.succeed();
        } finally {
            WandCastSessions.abort(caster);
            WizardTestSupport.retire(helper, caster);
            WizardTestSupport.retire(helper, outsider);
        }
    }
}
