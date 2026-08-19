package at.koopro.wizardsandbeasts.util;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One house style for command output.
 *
 * <p>Commands invented a header each: {@code --- Forms ---}, {@code === Module States ===},
 * {@code --- Steve's Heritage Profile ---}, {@code [W&B] }, and several with no header at all — six
 * shapes across ten files, each with its own colour choice. A report reads as one thing:
 *
 * <pre>
 * ┃ Apparition Licence
 * ┃ not yet held
 *   Training  · granted
 *   Practice  ▰▰▰▰▰▰▱▱▱▱▱▱  52%
 *   ─────────
 *   [ Sit the test ]
 * </pre>
 *
 * <p>Chat is the right home for this and stays that way — a command response is a transcript the
 * player asked for and may want to scroll back to. Gameplay events go through
 * {@code PlayerFeedback} instead.
 *
 * <p>Three rules the vocabulary is built on:
 *
 * <ul>
 *   <li><b>No space alignment.</b> Minecraft's font is proportional, so padding two labels to the
 *       same character count does not put their values in the same place. Columns are separated by a
 *       bullet instead, which is honest about it.</li>
 *   <li><b>Colour is never the only carrier.</b> A flag says "yes"/"no" as well as being green or
 *       red; a link is underlined as well as brass. Roughly one player in twelve cannot tell the
 *       first two apart.</li>
 *   <li><b>Style with {@code withStyle}, never {@code §} codes in the string</b>, the way
 *       {@link ChatHelper}'s builders do — a component whose colour is baked into its text cannot be
 *       restyled or nested by a caller.</li>
 * </ul>
 */
@NullMarked
public final class ChatReport {

    /**
     * Left rule on the header, the one piece of furniture every report shares.
     *
     * <p>{@code ▌} rather than the heavier {@code ┃} this used to be, for a reason worth keeping: the
     * glyphs Minecraft is <em>guaranteed</em> to draw are the ones in {@code font/ascii.png} and
     * {@code font/nonlatin_european.png}. Everything else falls through to unifont, which is a separate
     * downloaded asset — present in a normal install, absent often enough (stripped resource packs,
     * offline first launches) that a header rendering as a missing-glyph box is a real outcome.
     * {@code ┃} is not in either bitmap provider. Every glyph this class uses now is.
     */
    private static final String RULE = "▌ ";
    private static final String BULLET = " · ";
    private static final String INDENT = "  ";
    private static final String SUB_INDENT = "    ";

    /**
     * Filled and empty cells of a progress bar — a solid block against a light shade.
     *
     * <p>Both are in {@code font/ascii.png}, so they cannot fall through to unifont; see {@link #RULE}.
     * They are also the same advance width as each other, which is what stops a bar reflowing as it
     * fills — {@code ▰}/{@code ▱} would have been neither.
     */
    private static final char BAR_FULL = '█';
    private static final char BAR_EMPTY = '░';
    private static final int BAR_CELLS = 12;

    private final List<Component> lines = new ArrayList<>();

    private ChatReport() {}

    public static ChatReport of(Component title) {
        ChatReport report = new ChatReport();
        report.lines.add(Component.literal(RULE).withStyle(ChatPalette.color(ChatPalette.RULE))
                .append(title.copy().withStyle(ChatPalette.color(ChatPalette.ACCENT))));
        return report;
    }

    public static ChatReport of(String title) {
        return of(Component.literal(title));
    }

    /**
     * A second header line carrying the report's own state — "not yet held", "3 of 28 enabled".
     * Continues the rule, so the two lines read as one block rather than a header and a stray row.
     */
    public ChatReport subtitle(Component text) {
        lines.add(Component.literal(RULE).withStyle(ChatPalette.color(ChatPalette.RULE))
                .append(styled(text, ChatPalette.color(ChatPalette.MUTED))));
        return this;
    }

    public ChatReport subtitle(String text) {
        return subtitle(Component.literal(text));
    }

    /** A labelled value: {@code   Heritage · Wizardkind}. */
    public ChatReport row(String label, Component value) {
        lines.add(Component.literal(INDENT + label).withStyle(ChatPalette.color(ChatPalette.LABEL))
                .append(Component.literal(BULLET).withStyle(ChatPalette.color(ChatPalette.RULE)))
                .append(styled(value, ChatPalette.color(ChatPalette.TEXT))));
        return this;
    }

    public ChatReport row(String label, String value) {
        return row(label, Component.literal(value));
    }

    /** A row whose value explains itself on hover, for anything that needs a sentence of context. */
    public ChatReport row(String label, Component value, Component hover) {
        lines.add(Component.literal(INDENT + label).withStyle(ChatPalette.color(ChatPalette.LABEL))
                .append(Component.literal(BULLET).withStyle(ChatPalette.color(ChatPalette.RULE)))
                .append(styled(value, ChatPalette.color(ChatPalette.TEXT))
                        .copy().withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(hover)))));
        return this;
    }

    /** A yes/no value, worded as well as coloured so it does not rely on colour alone. */
    public ChatReport flag(String label, boolean value) {
        return row(label, Component.literal(value ? "yes" : "no")
                .withStyle(ChatPalette.color(value ? ChatPalette.OK : ChatPalette.BAD)));
    }

    /**
     * A state word in its own colour: {@code   Creatures · PREVIEW}. For enums a player reads as a
     * status rather than a value — module states, grades, tiers.
     */
    public ChatReport state(String label, String state, int rgb) {
        return row(label, Component.literal(state).withStyle(ChatPalette.color(rgb)));
    }

    /**
     * A state row that prefills a command on click rather than running one.
     *
     * <p>{@link ClickEvent.SuggestCommand} rather than {@link ClickEvent.RunCommand}: these rows sit in
     * lists of things an operator is inspecting, and a row that changes what it is reporting the instant
     * it is clicked is a trap. Prefilling puts the command in the box with the argument already right,
     * and leaves pressing enter to the person.
     */
    public ChatReport state(String label, String state, int rgb, String suggestCommand,
                            @Nullable Component hover) {
        MutableComponent value = Component.literal(state).withStyle(style -> {
            style = ChatPalette.color(rgb).withClickEvent(new ClickEvent.SuggestCommand(suggestCommand));
            return hover == null ? style : style.withHoverEvent(new HoverEvent.ShowText(hover));
        });
        return row(label, value);
    }

    /**
     * A progress bar, for anything with a floor and a ceiling. {@code progress} is 0–1 and is clamped;
     * the percentage is printed beside it because twelve cells cannot resolve better than 8% and a
     * player watching a long grind wants to see the number move.
     */
    public ChatReport bar(String label, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        lines.add(meterLine(label, clamped, Math.round(clamped * 100.0f) + "%"));
        return this;
    }

    /**
     * One row of a comparable set: {@code   █████░░░░░░░  42/100  Power}.
     *
     * <p>Two differences from {@link #bar}, both deliberate.
     *
     * <p>It prints the value rather than a percentage, because calling 42 out of 100 "42%" asserts it
     * is a proportion of something, and the next stat scored out of ten would read wrong in the same
     * place.
     *
     * <p><b>The track comes first and the label last.</b> Minecraft's font is proportional, so a
     * label-first row starts its track wherever that label happened to end — "Willpower" is wider than
     * "Power", and a column of meters comes out ragged with no two bars beginning at the same x. The
     * whole reason to draw a set of meters is to compare them at a glance, so the bars are what has to
     * line up, and only a leading fixed-width track can. It reads slightly backwards for a single row,
     * which is what {@link #bar} is for.
     */
    public ChatReport meter(String label, int value, int max, @Nullable Component hover) {
        float ratio = max <= 0 ? 0.0f : (float) value / max;
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        int filled = Math.round(clamped * BAR_CELLS);
        String track = String.valueOf(BAR_FULL).repeat(filled)
                + String.valueOf(BAR_EMPTY).repeat(BAR_CELLS - filled);
        int tone = clamped >= 1.0f ? ChatPalette.OK : ChatPalette.ACCENT;

        MutableComponent line = Component.literal(INDENT)
                .append(Component.literal(track).withStyle(ChatPalette.color(tone)))
                .append(Component.literal("  " + value + "/" + max)
                        .withStyle(ChatPalette.color(ChatPalette.MUTED)))
                .append(Component.literal("  " + label)
                        .withStyle(ChatPalette.color(ChatPalette.LABEL)));
        if (hover != null) {
            line = line.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
        }
        lines.add(line);
        return this;
    }

    public ChatReport meter(String label, int value, int max) {
        return meter(label, value, max, null);
    }

    private MutableComponent meterLine(String label, float ratio, String readout) {
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        int filled = Math.round(clamped * BAR_CELLS);
        String track = String.valueOf(BAR_FULL).repeat(filled)
                + String.valueOf(BAR_EMPTY).repeat(BAR_CELLS - filled);
        int tone = clamped >= 1.0f ? ChatPalette.OK : ChatPalette.ACCENT;
        return Component.literal(INDENT + label + " ").withStyle(ChatPalette.color(ChatPalette.LABEL))
                .append(Component.literal(track).withStyle(ChatPalette.color(tone)))
                .append(Component.literal("  " + readout).withStyle(ChatPalette.color(ChatPalette.MUTED)));
    }

    /** An entry in a list, indented one level under the header. */
    public ChatReport item(Component text) {
        lines.add(Component.literal(INDENT).append(styled(text, ChatPalette.color(ChatPalette.TEXT))));
        return this;
    }

    public ChatReport item(String text) {
        return item(Component.literal(text));
    }

    /** A child of the preceding {@link #item}, indented one level further. */
    public ChatReport subItem(Component text) {
        lines.add(Component.literal(SUB_INDENT).append(styled(text, ChatPalette.color(ChatPalette.LABEL))));
        return this;
    }

    public ChatReport subItem(String text) {
        return subItem(Component.literal(text));
    }

    /**
     * A clickable next step: {@code [ Sit the test ]}, running {@code command} on click.
     *
     * <p>Worth its own affordance because the alternative is telling the player to type a command,
     * which means they have to read it exactly and retype it exactly. Underlined as well as coloured
     * so it is visibly interactive without relying on the colour.
     *
     * @param command the full command including its leading slash
     */
    public ChatReport action(String text, String command, @Nullable Component hover) {
        MutableComponent link = Component.literal("[ " + text + " ]")
                .withStyle(ChatPalette.color(ChatPalette.LINK)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command)));
        if (hover != null) {
            link = link.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
        }
        lines.add(Component.literal(INDENT).append(link));
        return this;
    }

    public ChatReport action(String text, String command) {
        return action(text, command, null);
    }

    /** A hairline between sections of a long report. */
    public ChatReport divider() {
        lines.add(Component.literal(INDENT + "─────────")
                .withStyle(ChatPalette.color(ChatPalette.RULE)));
        return this;
    }

    /** A trailing note: counts, hints, "none found". */
    public ChatReport note(Component text) {
        lines.add(Component.literal(INDENT).append(styled(text, ChatPalette.color(ChatPalette.MUTED))));
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

    /**
     * Applies {@code fallback} only where the caller has not already styled the component.
     *
     * <p>{@code withStyle(Style)} is the wrong tool here: it resolves as {@code argument.applyTo(existing)},
     * and every field the argument sets wins. Passing a body colour that way silently repainted anything a
     * caller had already coloured — a state pill, a red refusal reason — so a value arrived here styled and
     * left uniform. Reversing the operands makes the caller's intent win and this a default.
     */
    private static Component styled(Component value, net.minecraft.network.chat.Style fallback) {
        return value.copy().withStyle(existing -> existing.applyTo(fallback));
    }

    /** Visible for testing: the rendered lines, in order. */
    public List<String> renderForTest() {
        return lines.stream().map(Component::getString).toList();
    }

    /** Visible for testing: the built components, for asserting on style rather than text. */
    public List<Component> componentsForTest() {
        return List.copyOf(lines);
    }

    /** Kept so callers that still reach for a vanilla colour compile; prefer {@link ChatPalette}. */
    static int legacy(ChatFormatting formatting) {
        Integer value = formatting.getColor();
        return value == null ? ChatPalette.TEXT : value;
    }
}
