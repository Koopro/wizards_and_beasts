package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperation;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperationService;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperations;
import at.koopro.wizardsandbeasts.network.admin.AdminReportPayloads;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the report/operation framework promises without a server: views are bounded and filter honestly, the wire
 * round-trips and refuses oversized input, the services refuse before they look anything up, and every registered
 * report and operation has its words.
 */
class AdminReportFrameworkTest {

    private static final AdminContext NOBODY = AdminContext.detached(null, "nobody", Set.of());
    private static final AdminContext VISUAL_ONLY = AdminContext.detached(null, "artist", EnumSet.of(AdminCapability.VISUAL));

    @Test
    void theBuilderGroupsLinesAndMarksEmptySections() {
        ReportView view = new ReportBuilder("demo").subject("s").section("first").row("a", 1).literal("b", "two")
                .section("empty").section("third").note("n").build();
        assertEquals("s", view.subject());
        assertEquals(3, view.sections().size());
        assertEquals("admin.wizards_and_beasts.report.demo.first", view.sections().get(0).heading());
        assertEquals(2, view.sections().get(0).lines().size());
        // An empty section says "nothing" rather than vanishing, so empty is not mistaken for missing.
        assertEquals(ReportBuilder.KEY_ROOT + "none", view.sections().get(1).lines().get(0).label());
    }

    @Test
    void theBuilderStopsAtItsLimitAndSaysSo() {
        ReportBuilder out = new ReportBuilder("big").section("all");
        for (int i = 0; i < ReportBuilder.MAX_LINES + 50; i++) {
            out.literal("line " + i, i);
        }
        ReportView view = out.build();
        assertEquals(ReportBuilder.MAX_LINES + 1, view.lineCount());
        assertEquals(ReportBuilder.KEY_ROOT + "truncated", view.sections().getLast().lines().get(0).label());
    }

    @Test
    void longTextIsClipped() {
        ReportView view = new ReportBuilder("clip").literal("x".repeat(500), "y".repeat(500)).build();
        assertTrue(view.sections().get(0).lines().get(0).label().length() <= ReportBuilder.MAX_TEXT);
        assertTrue(view.sections().get(0).lines().get(0).value().length() <= ReportBuilder.MAX_TEXT);
    }

    @Test
    void filteringKeepsMatchingLinesAndWholeMatchingSections() {
        ReportView view = new ReportBuilder("f").literalSection("Owls").literal("Hedwig", "snowy")
                .literal("Errol", "grey").literalSection("Cats").literal("Crookshanks", "ginger").build();
        ReportView owls = view.filtered("owls");
        assertEquals(1, owls.sections().size());
        assertEquals(2, owls.lineCount());
        ReportView ginger = view.filtered("GINGER");
        assertEquals(1, ginger.lineCount());
        assertEquals("Cats", ginger.sections().get(0).heading());
        assertEquals(0, view.filtered("basilisk").lineCount());
    }

    @Test
    void aReplyRoundTripsOnTheWire() {
        ReportView view = new ReportBuilder("wire").subject("abc").section("s").row("r", 3, ReportView.Tone.WARN)
                .rowKey("k", "some.key", ReportView.Tone.GOOD).subjectOption("abc", "Abc", "3m")
                .action("creature_heal", "heal", "", false).actionOn("economy_market_reset", "all", "all", "", true)
                .build();
        AdminReportPayloads.ReportReply reply = new AdminReportPayloads.ReportReply("wire", "abc", "ok", view);
        ByteBuf buf = Unpooled.buffer();
        AdminReportPayloads.ReportReply.STREAM_CODEC.encode(buf, reply);
        assertEquals(reply, AdminReportPayloads.ReportReply.STREAM_CODEC.decode(buf));
    }

    @Test
    void aRequestWithOversizedTextIsRefusedOnRead() {
        ByteBuf buf = Unpooled.buffer();
        AdminReportPayloads.ReportRequest.STREAM_CODEC.encode(buf,
                new AdminReportPayloads.ReportRequest("audit", "x".repeat(AdminReportService.MAX_SUBJECT + 1)));
        assertThrows(IllegalArgumentException.class, () -> AdminReportPayloads.ReportRequest.STREAM_CODEC.decode(buf));
        ByteBuf op = Unpooled.buffer();
        AdminReportPayloads.OperationRequest.STREAM_CODEC.encode(op, new AdminReportPayloads.OperationRequest(
                "creature_heal", "s", "y".repeat(AdminOperationService.MAX_ARGUMENT + 1), false));
        assertThrows(IllegalArgumentException.class, () -> AdminReportPayloads.OperationRequest.STREAM_CODEC.decode(op));
    }

    @Test
    void anOutsiderCannotTellWhichReportsOrOperationsExist() {
        // Both a real and an invented id are the same refusal for someone holding no capability at all.
        assertEquals(AdminReportService.Status.UNAUTHORIZED, AdminReportService.read(NOBODY, null, "audit", "").status());
        assertEquals(AdminReportService.Status.UNAUTHORIZED, AdminReportService.read(NOBODY, null, "no_such", "").status());
        assertEquals("unauthorized", AdminOperationService.perform(NOBODY, null, "creature_discard", "x", "", true).code());
        assertEquals("unauthorized", AdminOperationService.perform(NOBODY, null, "no_such", "x", "", true).code());
    }

    @Test
    void anAdministratorWithoutTheCapabilityIsRefused() {
        assertEquals(AdminReportService.Status.UNKNOWN, AdminReportService.read(VISUAL_ONLY, null, "no_such", "").status());
        assertEquals(AdminReportService.Status.UNAUTHORIZED,
                AdminReportService.read(VISUAL_ONLY, null, "diag_network", "").status());
        assertEquals("unknown", AdminOperationService.perform(VISUAL_ONLY, null, "no_such", "x", "", false).code());
        // Live creature operations act on the world: they need admin.world, not the section's capability.
        assertEquals("unauthorized", AdminOperationService.perform(VISUAL_ONLY, null, "creature_heal", "x", "", false).code());
    }

    @Test
    void idsAreUniqueAndWorldOperationsNeedWorld() {
        Set<String> seen = new HashSet<>();
        for (AdminReport report : AdminReports.all()) {
            assertTrue(seen.add(report.id()), report.id());
        }
        for (AdminOperation operation : AdminOperations.all()) {
            if (operation.id().startsWith("creature_")) {
                assertEquals(AdminCapability.WORLD, operation.capability(), operation.id());
            }
        }
        assertTrue(AdminOperations.byId("creature_discard").destructive());
        assertTrue(AdminOperations.byId("economy_market_reset").destructive());
        assertFalse(AdminOperations.byId("creature_heal").destructive());
    }

    @Test
    void everyReportAndOperationHasItsWords() throws Exception {
        JsonObject lang;
        try (InputStreamReader reader = new InputStreamReader(AdminReportFrameworkTest.class.getResourceAsStream(
                "/assets/wizards_and_beasts/lang/en_us.json"), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (AdminReport report : AdminReports.all()) {
            for (String part : List.of(".title", ".summary")) {
                assertNotNull(lang.get(ReportBuilder.KEY_ROOT + report.id() + part), report.id() + part);
            }
            assertNotNull(lang.get("admin.wizards_and_beasts.section." + report.section().id()), report.id());
        }
        for (AdminOperation operation : AdminOperations.all()) {
            String key = "admin.wizards_and_beasts.operation." + operation.id();
            assertNotNull(lang.get(key), key);
            assertNotNull(lang.get(key + ".desc"), key + ".desc");
            if (operation.destructive()) {
                assertNotNull(lang.get(key + ".warning"), key + ".warning");
            }
        }
        for (String code : List.of("unauthorized", "unknown", "invalid_argument", "confirm_required", "not_eligible",
                "failed")) {
            assertNotNull(lang.get("admin.wizards_and_beasts.report_page.code." + code), code);
        }
        for (AdminReportService.Status status : AdminReportService.Status.values()) {
            if (status != AdminReportService.Status.OK) {
                assertNotNull(lang.get("admin.wizards_and_beasts.report_page.status." + status.id()), status.id());
            }
        }
    }
}
