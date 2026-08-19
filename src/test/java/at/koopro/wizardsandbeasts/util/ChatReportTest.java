package at.koopro.wizardsandbeasts.util;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The house style, pinned where it is easy to break by accident.
 *
 * <p>Most of this is about {@code Style} rather than text, because the failures that matter here are
 * invisible in {@code getString()} — a colour quietly overwritten, a click event dropped — and a command
 * that renders is not the same as a command that renders right.
 */
class ChatReportTest {

    /** The style of the last child of a line, which is where a row's value ends up. */
    private static Style valueStyleOf(Component line) {
        List<Component> siblings = line.getSiblings();
        assertTrue(!siblings.isEmpty(), "line has no children: " + line.getString());
        return siblings.get(siblings.size() - 1).getStyle();
    }

    /**
     * The bug this guards was live and silent: {@code withStyle(Style)} resolves as
     * {@code argument.applyTo(existing)}, so the body colour a row applies won its argument's colour and
     * repainted anything the caller had already styled — a state pill, a red refusal — to plain body text.
     */
    @Test
    void aCallerSuppliedColourSurvivesBeingPutInARow() {
        Component preColoured = Component.literal("PREVIEW")
                .withStyle(ChatPalette.color(ChatPalette.WARN));
        Component line = ChatReport.of("Modules").row("Creatures", preColoured)
                .componentsForTest().get(1);

        assertEquals(TextColor.fromRgb(ChatPalette.WARN), valueStyleOf(line).getColor(),
                "the row repainted a value the caller had already coloured");
    }

    @Test
    void flagReadsAsWordsNotJustColour() {
        List<String> lines = ChatReport.of("T").flag("Training", true).flag("Licence", false)
                .renderForTest();
        assertTrue(lines.get(1).contains("yes"),
                "a true flag must say so, not only be green — colour alone excludes a lot of players");
        assertTrue(lines.get(2).contains("no"));
    }

    @Test
    void barIsClampedAtBothEnds() {
        assertTrue(ChatReport.of("T").bar("Over", 5.0f).renderForTest().get(1).contains("100%"),
                "a bar past its ceiling must read 100%, not 500%");
        assertTrue(ChatReport.of("T").bar("Under", -3.0f).renderForTest().get(1).contains("0%"));
    }

    @Test
    void barTrackIsAlwaysTheSameWidth() {
        // A track that changes width between calls makes a list of bars unreadable, which is the one
        // thing a bar is for.
        int expected = -1;
        for (int percent = 0; percent <= 100; percent += 7) {
            String rendered = ChatReport.of("T").bar("X", percent / 100.0f).renderForTest().get(1);
            long cells = rendered.chars().filter(c -> c == '█' || c == '░').count();
            if (expected < 0) {
                expected = (int) cells;
            }
            assertEquals(expected, cells, "bar width changed at " + percent + "%");
        }
    }

    @Test
    void meterShowsTheRealNumberNotAPercentage() {
        String rendered = ChatReport.of("T").meter("Power", 42, 100).renderForTest().get(1);
        assertTrue(rendered.contains("42/100"),
                "a meter must print its value — calling 42 out of 100 \"42%\" asserts it is a "
                        + "proportion, and the next stat scored out of ten would read wrong here");
    }

    @Test
    void meterHandlesAZeroMaximumWithoutDividingByIt() {
        String rendered = ChatReport.of("T").meter("Nothing", 3, 0).renderForTest().get(1);
        assertTrue(rendered.contains("3/0"), "a zero maximum must still render");
    }

    @Test
    void anActionCarriesARunnableCommandAndIsUnderlined() {
        Component line = ChatReport.of("T")
                .action("Sit the test", "/wandb magic apparate test", Component.literal("why"))
                .componentsForTest().get(1);
        Style style = valueStyleOf(line);

        assertTrue(style.getClickEvent() instanceof ClickEvent.RunCommand run
                        && run.command().equals("/wandb magic apparate test"),
                "the action lost its command, so it is a coloured label that does nothing");
        assertTrue(style.isUnderlined(),
                "interactive text must not be signalled by colour alone");
        assertNotNull(style.getHoverEvent());
    }

    /**
     * A state row prefills rather than runs. Clicking a row in a list of things you are inspecting and
     * having it change what it reports is a trap, so this distinction is deliberate and worth pinning.
     */
    @Test
    void aStateRowSuggestsRatherThanRuns() {
        Component line = ChatReport.of("T")
                .state("Creatures", "preview", ChatPalette.WARN,
                        "/wandb admin module set creatures ", null)
                .componentsForTest().get(1);
        Style style = valueStyleOf(line);

        assertTrue(style.getClickEvent() instanceof ClickEvent.SuggestCommand,
                "a state row must prefill, never run — otherwise inspecting a module changes it");
        assertEquals(TextColor.fromRgb(ChatPalette.WARN), style.getColor());
    }

    @Test
    void rowHoverIsAttachedToTheValue() {
        Component line = ChatReport.of("T")
                .row("Label", Component.literal("value"), Component.literal("explanation"))
                .componentsForTest().get(1);
        HoverEvent hover = valueStyleOf(line).getHoverEvent();
        assertTrue(hover instanceof HoverEvent.ShowText show
                        && show.value().getString().equals("explanation"),
                "the hover explanation did not survive onto the value");
    }

    @Test
    void everyLineIsSeparateSoIndentationSurvives() {
        // The chat log wraps a multi-line component as one entry and re-indents it, which would flatten
        // the structure the whole vocabulary is built out of.
        List<Component> lines = ChatReport.of("Title").row("a", "1").note("done").componentsForTest();
        assertEquals(3, lines.size());
        for (Component line : lines) {
            assertTrue(!line.getString().contains("\n"), "a report line contains a newline");
        }
    }

    /**
     * Every glyph the vocabulary uses must live in a bitmap font provider.
     *
     * <p>Anything outside {@code font/ascii.png} and {@code font/nonlatin_european.png} falls through to
     * unifont, a separately downloaded asset. Normally present; absent often enough that relying on it
     * means a header sometimes renders as a row of missing-glyph boxes. This caught {@code ┃}, which
     * the first version of this class shipped with.
     */
    @Test
    void everyGlyphIsOneMinecraftIsGuaranteedToDraw() {
        // The CP437 box/block range in ascii.png, plus the punctuation in nonlatin_european.png.
        String guaranteed = "─│┌┐└┘├┤┬┴┼"
                + "═║▀▄█▌▐░▒▓·—";

        String rendered = String.join(System.lineSeparator(), ChatReport.of("Title")
                .subtitle("sub")
                .row("label", "value")
                .flag("flag", true)
                .bar("bar", 0.5f)
                .meter("meter", 1, 2)
                .item("item")
                .subItem("sub item")
                .action("act", "/x")
                .divider()
                .note("note")
                .renderForTest());

        for (char c : rendered.toCharArray()) {
            boolean plainAscii = c >= 0x20 && c < 0x7F;
            if (plainAscii || Character.isWhitespace(c) || guaranteed.indexOf(c) >= 0) {
                continue;
            }
            throw new AssertionError(String.format(
                    "ChatReport emits U+%04X ('%c'), which is not in a bitmap font provider — it will "
                            + "render as a missing-glyph box wherever unifont is not available", (int) c, c));
        }
    }

    @Test
    void headerAndSubtitleShareTheRule() {
        List<String> lines = ChatReport.of("Apparition Licence").subtitle("not yet held").renderForTest();
        assertTrue(lines.get(0).startsWith("▌"));
        assertTrue(lines.get(1).startsWith("▌"),
                "the subtitle broke away from the header rule, so the two read as unrelated lines");
    }
}
