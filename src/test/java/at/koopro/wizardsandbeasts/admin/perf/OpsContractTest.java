package at.koopro.wizardsandbeasts.admin.perf;

import at.koopro.wizardsandbeasts.admin.config.catalog.ConfigSettingCatalog;
import at.koopro.wizardsandbeasts.admin.debug.CastDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.DebugLeases;
import at.koopro.wizardsandbeasts.admin.debug.LiveDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.ModLogLevel;
import at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Performance and Debug without a server: wire formats, preset data, classification, the cast ring's bounds, lang. */
class OpsContractTest {

    @Test
    void metricsRoundTripIncludingUnavailableValues() {
        PerformanceMetrics.Snapshot snapshot = new PerformanceMetrics.Snapshot(20f, PerformanceMetrics.UNAVAILABLE,
                PerformanceMetrics.UNAVAILABLE, PerformanceMetrics.UNAVAILABLE,
                List.of(new PerformanceMetrics.Dimension("minecraft:overworld", 120, 14, 2, 600, 1, PerformanceMetrics.UNAVAILABLE)),
                1, 3, 0, 1, 2, 5, 40f, 12f, PerformanceMetrics.UNAVAILABLE, false);
        AdminOpsPayloads.MetricsReply sent = new AdminOpsPayloads.MetricsReply(snapshot, "custom");
        ByteBuf buf = Unpooled.buffer();
        AdminOpsPayloads.MetricsReply.STREAM_CODEC.encode(buf, sent);
        AdminOpsPayloads.MetricsReply back = AdminOpsPayloads.MetricsReply.STREAM_CODEC.decode(buf);
        assertEquals(sent, back);
        assertEquals(PerformanceMetrics.UNAVAILABLE, back.metrics().tps());
        assertEquals(120, back.metrics().entities());
    }

    @Test
    void diagnosticsAndLeaseStateRoundTrip() {
        LiveDiagnostics.Snapshot live = new LiveDiagnostics.Snapshot(
                List.of(new LiveDiagnostics.Beam("Harry", "wizards_and_beasts:stupefy", 40, true)),
                List.of(new LiveDiagnostics.Session("Harry", 7, "stupefy", 12, false, true, false)),
                List.of(new CastDiagnostics.Event(1L, 2L, "Harry", "cast_reject", "cooldown")),
                List.of(new LiveDiagnostics.Count("cast_reject:cooldown", 3)),
                List.of(new LiveDiagnostics.Count("duelling_dummy", 2)), 2,
                List.of(new LiveDiagnostics.ModuleRow("ministry", "enabled")),
                List.of(new LiveDiagnostics.Connection("Harry", 30, 41f, 12f)),
                List.of(new LiveDiagnostics.Holding("Dev", "log_level", 4)));
        DebugLeases.State state = new DebugLeases.State(true, false, true, ModLogLevel.Choice.DEBUG,
                EnumSet.of(DebugLeases.Tool.MY_DEBUG_MODE, DebugLeases.Tool.LOG_LEVEL));
        AdminOpsPayloads.DiagnosticsReply sent = new AdminOpsPayloads.DiagnosticsReply(live, state);
        ByteBuf buf = Unpooled.buffer();
        AdminOpsPayloads.DiagnosticsReply.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, AdminOpsPayloads.DiagnosticsReply.STREAM_CODEC.decode(buf));
    }

    @Test
    void anInventedToolOrPresetIsRefusedAtDecode() {
        ByteBuf tool = Unpooled.buffer();
        tool.writeByte(DebugLeases.Tool.values().length + 2);
        assertThrows(RuntimeException.class, () -> AdminOpsPayloads.ToggleRequest.STREAM_CODEC.decode(tool));
        ByteBuf preset = Unpooled.buffer();
        preset.writeByte(-1);
        assertThrows(RuntimeException.class, () -> AdminOpsPayloads.PresetRequest.STREAM_CODEC.decode(preset));
    }

    @Test
    void presetsNameOnlyCatalogSettingsWithinTheirRanges() {
        Set<String> catalog = ConfigSettingCatalog.ENTRIES.stream().map(ConfigSettingCatalog.Entry::path)
                .collect(java.util.stream.Collectors.toSet());
        for (PerformancePresets.Preset preset : PerformancePresets.Preset.values()) {
            assertEquals(Set.of("perf_profile", "beam_target_scan_interval_ticks", "beam_channel_effect_interval_ticks"),
                    preset.settings().keySet(), preset.id());
            for (String path : preset.settings().keySet()) {
                assertTrue(catalog.contains(path), path);
            }
            int scan = Integer.parseInt(preset.settings().get("beam_target_scan_interval_ticks"));
            int effect = Integer.parseInt(preset.settings().get("beam_channel_effect_interval_ticks"));
            assertTrue(scan >= 1 && scan <= 20 && effect >= 1 && effect <= 20, preset.id());
        }
        // MEDIUM is the shipped defaults, so a fresh server reads as MEDIUM, not CUSTOM.
        assertEquals("2", PerformancePresets.Preset.MEDIUM.settings().get("beam_target_scan_interval_ticks"));
        assertEquals("5", PerformancePresets.Preset.MEDIUM.settings().get("beam_channel_effect_interval_ticks"));
        for (String path : PerformancePresets.CLASSIFICATION.keySet()) {
            assertTrue(catalog.contains(path), "classified setting not in the catalog: " + path);
            assertTrue(!PerformancePresets.CLASSIFICATION.get(path).isEmpty(), path);
        }
    }

    @Test
    void theCastRingIsBounded() {
        CastDiagnostics.clear();
        for (int i = 0; i < CastDiagnostics.CAPACITY * 3; i++) {
            CastDiagnostics.record("p", i, "cast_reject", "reason_" + i);
        }
        assertEquals(CastDiagnostics.CAPACITY, CastDiagnostics.recent(10_000).size());
        assertTrue(CastDiagnostics.counts(10_000).size() <= CastDiagnostics.MAX_KINDS);
        assertEquals("reason_" + (CastDiagnostics.CAPACITY * 3 - 1), CastDiagnostics.recent(1).get(0).detail());
        CastDiagnostics.clear();
    }

    @Test
    void everyOpsKeyHasWords() throws Exception {
        JsonObject lang;
        try (InputStreamReader reader = new InputStreamReader(OpsContractTest.class.getResourceAsStream(
                "/assets/wizards_and_beasts/lang/en_us.json"), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        String p = "admin.wizards_and_beasts.perf.";
        for (PerformancePresets.Preset preset : PerformancePresets.Preset.values()) {
            assertNotNull(lang.get(p + "preset." + preset.id()), preset.id());
            assertNotNull(lang.get(p + "preset." + preset.id() + ".desc"), preset.id());
        }
        for (PerformancePresets.Kind kind : PerformancePresets.Kind.values()) {
            assertNotNull(lang.get(p + "kind." + kind.name().toLowerCase(java.util.Locale.ROOT)), kind.name());
        }
        for (ModLogLevel.Choice choice : ModLogLevel.Choice.values()) {
            assertNotNull(lang.get("admin.wizards_and_beasts.debug_tools.level." + choice.id()), choice.id());
        }
        for (String code : List.of("unauthorized", "panel_only", "invalid_value")) {
            assertNotNull(lang.get("admin.wizards_and_beasts.debug_tools.refused." + code), code);
        }
    }
}
