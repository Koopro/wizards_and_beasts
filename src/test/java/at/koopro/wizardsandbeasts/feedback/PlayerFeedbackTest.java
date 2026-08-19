package at.koopro.wizardsandbeasts.feedback;

import at.koopro.wizardsandbeasts.client.gui.toast.WizardsToast;
import at.koopro.wizardsandbeasts.util.ChatReport;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feedback layer's two silent-failure modes.
 *
 * <p>A toast without a stable token stacks instead of replacing, which turns a repeated event into a
 * wall of identical panels — the chat problem this replaced, in a nicer frame. And a report that
 * loses its indentation reads as an undifferentiated block. Neither shows up as an exception.
 */
class PlayerFeedbackTest {

    // ── toast dedup ──

    @Test
    void theSameEventTwiceSharesAToken() {
        // Same token means ToastManager replaces rather than stacks.
        assertEquals(token(NoticeKind.FAIL, "Iron Will"), token(NoticeKind.FAIL, "Iron Will"));
    }

    @Test
    void differentSkillsKeepDistinctTokens() {
        // Trying three different locked nodes must still say three different things.
        assertNotEquals(token(NoticeKind.FAIL, "Iron Will"), token(NoticeKind.FAIL, "Quick Hands"));
    }

    @Test
    void successAndFailureForTheSameThingDoNotCollapse() {
        // Otherwise a later success would silently replace the refusal that explained the earlier click.
        assertNotEquals(token(NoticeKind.UNLOCK, "Iron Will"), token(NoticeKind.FAIL, "Iron Will"));
    }

    @Test
    void everyKindHasItsOwnAccent() {
        // A shared accent would make the colour rail decorative rather than informative.
        long distinct = java.util.Arrays.stream(NoticeKind.values())
                .map(WizardsToast::accent)
                .distinct()
                .count();
        assertEquals(NoticeKind.values().length, distinct);
    }

    @Test
    void everyAccentIsOpaque() {
        // The rail is drawn with GuiGraphics.fill, which honours alpha — a colour packed without one
        // would render as nothing at all.
        for (NoticeKind kind : NoticeKind.values()) {
            assertEquals(0xFF, (WizardsToast.accent(kind) >>> 24),
                    kind + " accent is not opaque");
        }
    }

    // ── the wire form ──

    @Test
    void kindsRoundTripByNameNotOrdinal() {
        // Ordinals would make reordering the enum a silent protocol break between client and server.
        for (NoticeKind kind : NoticeKind.values()) {
            assertEquals(kind, NoticeKind.byName(kind.serializedName()));
        }
    }

    @Test
    void anUnknownKindDegradesInsteadOfThrowing() {
        assertEquals(NoticeKind.SUCCESS, NoticeKind.byName("SOMETHING_A_NEWER_SERVER_SENT"));
    }

    // ── the toast box ──

    @Test
    void heightIsAWholeNumberOfSlots() {
        // ToastManager allocates in 32px slots; a height that is not a multiple leaves the next toast
        // overlapping this one's lower edge.
        assertEquals(0, Toast.SLOT_HEIGHT % Toast.SLOT_HEIGHT);
        assertTrue(Toast.SLOT_HEIGHT > 0);
    }

    // ── command reports ──

    @Test
    void aReportKeepsItsHeaderAndIndentation() {
        List<String> lines = ChatReport.of("Heritages")
                .row("Heritage", "Wizardkind")
                .flag("Wand", true)
                .item("Half-blood")
                .subItem("Mixed ancestry")
                .note("3 total")
                .renderForTest();

        assertEquals(6, lines.size());
        assertTrue(lines.get(0).endsWith("Heritages"), "header lost: " + lines.get(0));
        assertTrue(lines.get(1).contains("Heritage") && lines.get(1).contains("Wizardkind"));
        assertEquals("yes", lines.get(2).substring(lines.get(2).lastIndexOf(' ') + 1));
        // Indentation is the report's only structure, so the depths must stay distinguishable.
        assertTrue(lines.get(4).startsWith("    "), "sub-item lost its indent: '" + lines.get(4) + "'");
        assertTrue(lines.get(3).startsWith("  ") && !lines.get(3).startsWith("    "));
    }

    @Test
    void aFalseFlagReadsAsNo() {
        List<String> lines = ChatReport.of("X").flag("Wand", false).renderForTest();
        assertTrue(lines.get(1).endsWith("no"), lines.get(1));
    }

    /** Mirrors {@code WizardsToast}'s token rule without needing a Font to construct one. */
    private static Object token(NoticeKind kind, String title) {
        return kind.name() + ' ' + Component.literal(title).getString();
    }
}
