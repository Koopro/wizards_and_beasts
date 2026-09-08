package at.koopro.wizardsandbeasts.command.debug.report;

import at.koopro.wizardsandbeasts.util.ChatPalette;
import at.koopro.wizardsandbeasts.util.ChatReport;
import net.minecraft.commands.CommandSourceStack;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A debug dump, built once and rendered wherever it is wanted.
 *
 * <h2>Why not just build a {@link ChatReport}</h2>
 *
 * <p>Because a chat report can only go to chat. The cauldron dump was a wall of thirty lines that
 * scrolled the rest of the log away every time you glanced at a pot, and the fix — showing it beside
 * the cauldron instead — needs the same content on the client. A {@code ChatReport} is a list of
 * styled {@code Component}s aimed at a {@code CommandSourceStack}; there is nothing in it a renderer
 * on the far side of the network could use.
 *
 * <p>So the content is collected here as plain data, and the two renderers are both thin: this
 * class's {@link #send} for chat, and {@code WorldDebugPanel} for the floating box. A feature writes
 * its dump once and gets both.
 *
 * <p>The builder vocabulary is deliberately a subset of {@link ChatReport}'s, and maps onto it
 * one-for-one, so anything already written against the house style reads the same after moving here.
 */
@NullMarked
public final class DebugReport {

    /** Rows in one report. A dump longer than this is a bug in the dump, not a limit worth raising. */
    public static final int MAX_LINES = 96;

    private final String title;
    private final List<DebugLine> lines = new ArrayList<>();

    private DebugReport(String title) {
        this.title = title;
    }

    public static DebugReport of(String title) {
        return new DebugReport(title);
    }

    /** Rebuilds a report received off the wire. */
    public static DebugReport of(String title, List<DebugLine> lines) {
        DebugReport report = new DebugReport(title);
        report.lines.addAll(lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines);
        return report;
    }

    public String title() {
        return title;
    }

    public List<DebugLine> lines() {
        return List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public DebugReport section(String text) {
        return add(new DebugLine(DebugLine.Kind.SECTION, text, "", DebugLine.DEFAULT_COLOUR, 0f));
    }

    public DebugReport row(String label, String value) {
        return add(new DebugLine(DebugLine.Kind.ROW, label, value, DebugLine.DEFAULT_COLOUR, 0f));
    }

    public DebugReport row(String label, Object value) {
        return row(label, String.valueOf(value));
    }

    /** A value in its own colour, for anything read as a status rather than a number. */
    public DebugReport state(String label, String value, int rgb) {
        return add(new DebugLine(DebugLine.Kind.ROW, label, value, rgb, 0f));
    }

    /**
     * A yes/no. Worded as well as coloured, for the same reason {@link ChatReport#flag} is: roughly
     * one player in twelve cannot tell the two colours apart.
     */
    public DebugReport flag(String label, boolean value) {
        return add(new DebugLine(DebugLine.Kind.FLAG, label, value ? "yes" : "no",
                value ? ChatPalette.OK : ChatPalette.BAD, 0f));
    }

    public DebugReport bar(String label, float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        return add(new DebugLine(DebugLine.Kind.BAR, label, Math.round(clamped * 100f) + "%",
                DebugLine.DEFAULT_COLOUR, clamped));
    }

    public DebugReport note(String text) {
        return add(new DebugLine(DebugLine.Kind.NOTE, text, "", DebugLine.DEFAULT_COLOUR, 0f));
    }

    /** Something the dump found wrong. Both renderers make this loud. */
    public DebugReport warn(String text) {
        return add(new DebugLine(DebugLine.Kind.WARN, text, "", ChatPalette.BAD, 0f));
    }

    private DebugReport add(DebugLine line) {
        if (lines.size() < MAX_LINES) {
            lines.add(line);
        }
        return this;
    }

    /**
     * The chat renderer. Rebuilds the report as a {@link ChatReport} so command output keeps the
     * house style rather than growing a second one beside it.
     */
    public void send(CommandSourceStack source) {
        ChatReport chat = ChatReport.of(title);
        for (DebugLine line : lines) {
            switch (line.kind()) {
                case SECTION -> chat.subtitle(line.label());
                case ROW -> {
                    if (line.rgb() == DebugLine.DEFAULT_COLOUR) {
                        chat.row(line.label(), line.value());
                    } else {
                        chat.state(line.label(), line.value(), line.rgb());
                    }
                }
                case FLAG -> chat.flag(line.label(), "yes".equals(line.value()));
                case BAR -> chat.bar(line.label(), line.progress());
                case NOTE -> chat.note(line.label());
                case WARN -> chat.subtitle(net.minecraft.network.chat.Component.literal(line.label())
                        .withStyle(ChatPalette.color(ChatPalette.BAD)));
            }
        }
        chat.send(source);
    }
}
