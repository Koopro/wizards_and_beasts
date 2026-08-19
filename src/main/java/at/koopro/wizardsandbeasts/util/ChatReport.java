package at.koopro.wizardsandbeasts.util;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * One house style for command output.
 *
 * <p>Commands invented a header each: {@code --- Forms ---}, {@code === Module States ===},
 * {@code --- Steve's Heritage Profile ---}, {@code [W&B] }, and several with no header at all — six
 * shapes across ten files, each with its own colour choice. A report reads as one thing now:
 *
 * <pre>
 * ┃ Heritages &amp; Variants
 *   Wizardkind · Human magic, medium
 *     Half-blood (half_blood)
 * </pre>
 *
 * <p>Chat is the right home for this and stays that way — a command response is a transcript the
 * player asked for and may want to scroll back to. Gameplay events go through
 * {@code PlayerFeedback} instead.
 *
 * <p>Styling is applied with {@code withStyle}, never by prefixing {@code §} codes into the string
 * the way {@link ChatHelper}'s builders do; a component whose colour is baked into its text cannot be
 * restyled or nested by a caller.
 */
@NullMarked
public final class ChatReport {

    /** Left rule on the header, the one piece of furniture every report shares. */
    private static final String RULE = "┃ ";
    private static final String BULLET = " · ";

    private final List<Component> lines = new ArrayList<>();

    private ChatReport() {}

    public static ChatReport of(Component title) {
        ChatReport report = new ChatReport();
        report.lines.add(Component.literal(RULE).withStyle(ChatFormatting.DARK_GRAY)
                .append(title.copy().withStyle(ChatFormatting.GOLD)));
        return report;
    }

    public static ChatReport of(String title) {
        return of(Component.literal(title));
    }

    /** A labelled value: {@code   Heritage · Wizardkind}. */
    public ChatReport row(String label, Component value) {
        lines.add(Component.literal("  " + label).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(BULLET).withStyle(ChatFormatting.DARK_GRAY))
                .append(value.copy().withStyle(ChatFormatting.WHITE)));
        return this;
    }

    public ChatReport row(String label, String value) {
        return row(label, Component.literal(value));
    }

    /** A yes/no value, coloured so it can be read without parsing the word. */
    public ChatReport flag(String label, boolean value) {
        return row(label, Component.literal(value ? "yes" : "no")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    /** An entry in a list, indented one level under the header. */
    public ChatReport item(Component text) {
        lines.add(Component.literal("  ").append(text.copy().withStyle(ChatFormatting.WHITE)));
        return this;
    }

    public ChatReport item(String text) {
        return item(Component.literal(text));
    }

    /** A child of the preceding {@link #item}, indented one level further. */
    public ChatReport subItem(Component text) {
        lines.add(Component.literal("    ").append(text.copy().withStyle(ChatFormatting.GRAY)));
        return this;
    }

    public ChatReport subItem(String text) {
        return subItem(Component.literal(text));
    }

    /** A trailing note: counts, hints, "none found". */
    public ChatReport note(Component text) {
        lines.add(Component.literal("  ").append(text.copy().withStyle(ChatFormatting.DARK_GRAY)));
        return this;
    }

    public ChatReport note(String text) {
        return note(Component.literal(text));
    }

    /**
     * Sends every line.
     *
     * <p>{@code sendSuccess} is called once per line rather than joining with {@code \n} because the
     * chat log wraps and indents a multi-line component as one entry, which loses the indentation the
     * report is built out of.
     */
    public void send(CommandSourceStack source) {
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
    }

    /** Visible for testing: the rendered lines, in order. */
    public List<String> renderForTest() {
        return lines.stream().map(Component::getString).toList();
    }
}
