package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSections;
import at.koopro.wizardsandbeasts.command.debug.report.DebugLine;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.network.debug.DebugInspectResultPayload;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The report model and the wire that carries it.
 *
 * <p>The round trip is the test that matters. A report is built on the server and drawn on the
 * client, so a field that encodes but does not decode does not fail loudly — it draws a panel with a
 * row silently missing, which is indistinguishable from the subsystem not having that state.
 */
class DebugReportTest {

    @Test
    void buildersProduceTheKindsTheRenderersSwitchOn() {
        DebugReport report = DebugReport.of("Cauldron")
                .row("tier", "pewter")
                .flag("filled", true)
                .flag("heated", false)
                .bar("progress", 0.5f)
                .state("phase", "BREWING", ChatPalette.ACCENT)
                .section("contents")
                .note("nothing in it")
                .warn("visual mismatch");

        List<DebugLine> lines = report.lines();
        assertEquals(8, lines.size());
        assertEquals(DebugLine.Kind.ROW, lines.get(0).kind());
        assertEquals(DebugLine.Kind.FLAG, lines.get(1).kind());
        assertEquals("yes", lines.get(1).value());
        assertEquals("no", lines.get(2).value());
        assertEquals(DebugLine.Kind.BAR, lines.get(3).kind());
        assertEquals("50%", lines.get(3).value());
        assertEquals(ChatPalette.ACCENT, lines.get(4).rgb());
        assertEquals(DebugLine.Kind.SECTION, lines.get(5).kind());
        assertEquals(DebugLine.Kind.NOTE, lines.get(6).kind());
        assertEquals(DebugLine.Kind.WARN, lines.get(7).kind());
    }

    /**
     * A flag must not rely on colour alone to say which way it went, so the words differ as well —
     * and the chat renderer reconstructs the boolean from the word, which only works while they do.
     */
    @Test
    void aFlagIsWordedAsWellAsColoured() {
        DebugReport report = DebugReport.of("t").flag("on", true).flag("off", false);
        assertNotEquals(report.lines().get(0).value(), report.lines().get(1).value());
        assertEquals(ChatPalette.OK, report.lines().get(0).rgb());
        assertEquals(ChatPalette.BAD, report.lines().get(1).rgb());
    }

    @Test
    void barsAreClampedBeforeTheyReachARenderer() {
        DebugReport report = DebugReport.of("t").bar("over", 4.0f).bar("under", -2.0f);
        assertEquals(1.0f, report.lines().get(0).progress());
        assertEquals(0.0f, report.lines().get(1).progress());
    }

    /** The cap is what stops a runaway section from writing a packet nobody can decode. */
    @Test
    void aReportStopsAtItsLineCap() {
        DebugReport report = DebugReport.of("t");
        for (int i = 0; i < DebugReport.MAX_LINES * 2; i++) {
            report.row("row" + i, i);
        }
        assertEquals(DebugReport.MAX_LINES, report.lines().size());
    }

    @Test
    void aReportSurvivesTheTripToTheClient() {
        DebugReport built = DebugReport.of("Cauldron @ 1, 2, 3")
                .row("tier", "pewter")
                .flag("filled", true)
                .bar("progress", 0.25f)
                .state("phase", "BREWING", ChatPalette.ACCENT)
                .warn("visual mismatch");

        DebugInspectResultPayload sent = DebugInspectResultPayload.of(new Vec3(1.5, 2.5, 3.5), built);
        ByteBuf buf = Unpooled.buffer();
        DebugInspectResultPayload.STREAM_CODEC.encode(buf, sent);
        DebugInspectResultPayload received = DebugInspectResultPayload.STREAM_CODEC.decode(buf);

        assertEquals(sent.title(), received.title());
        assertEquals(1.5, received.x());
        assertEquals(2.5, received.y());
        assertEquals(3.5, received.z());
        assertEquals(built.lines(), received.lines());
    }

    @Test
    void anEmptyResultIsHowTheServerSaysNothingIsThere() {
        ByteBuf buf = Unpooled.buffer();
        DebugInspectResultPayload.STREAM_CODEC.encode(buf, DebugInspectResultPayload.empty());
        assertTrue(DebugInspectResultPayload.STREAM_CODEC.decode(buf).lines().isEmpty());
    }

    /**
     * Ids are the command literals. Two sections sharing one would silently shadow each other in the
     * brigadier tree, and the loser would be unreachable rather than an error.
     */
    @Test
    void everyFeatureSectionHasAUniqueIdAndATitle() {
        Set<String> seen = new HashSet<>();
        for (FeatureDebugSection section : FeatureDebugSections.all()) {
            assertTrue(seen.add(section.id()), "duplicate section id: " + section.id());
            assertFalse(section.id().isBlank(), "section with no id");
            assertFalse(section.title().isBlank(), "section with no title: " + section.id());
            assertEquals(section.id().toLowerCase(java.util.Locale.ROOT), section.id(),
                    "section id must be a lowercase command literal: " + section.id());
            assertFalse(section.id().contains(" "),
                    "section id must be one command literal: " + section.id());
        }
        assertFalse(seen.isEmpty(), "no feature sections registered");
    }

    /**
     * {@code all} and {@code brief} are literals on the same node as the section ids, so a section
     * called either of them would take over the node that prints every section.
     */
    @Test
    void noSectionClaimsAReservedLiteral() {
        assertFalse(FeatureDebugSections.ids().contains("all"));
        assertFalse(FeatureDebugSections.ids().contains("brief"));
    }

    /** Modules explain why everything above them did nothing, so they read last. */
    @Test
    void theModuleSectionIsLast() {
        List<String> ids = FeatureDebugSections.ids();
        assertEquals("modules", ids.get(ids.size() - 1));
    }
}
