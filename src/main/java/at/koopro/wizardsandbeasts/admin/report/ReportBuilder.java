package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.report.ReportView.Action;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Line;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Section;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Subject;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Tone;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link ReportView}. Bounded: past {@link #MAX_LINES} lines, {@link #MAX_SUBJECTS} subjects or
 * {@link #MAX_ACTIONS} actions it stops taking more and says so in a last line, so one large world cannot make a
 * reply that the wire refuses. Text is clipped to {@link #MAX_TEXT}.
 *
 * <p>Labels written with {@link #row} and headings with {@link #section} are lang keys under
 * {@code admin.wizards_and_beasts.report.<report id>.}; the {@code literal} variants take text as is (names, ids).
 */
@NullMarked
public final class ReportBuilder {

    public static final int MAX_LINES = 600;
    public static final int MAX_SUBJECTS = 128;
    public static final int MAX_ACTIONS = 16;
    public static final int MAX_TEXT = 200;
    public static final String KEY_ROOT = "admin.wizards_and_beasts.report.";

    private final String prefix;
    private final List<Section> sections = new ArrayList<>();
    private final List<Subject> subjects = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private String subject = "";
    private @Nullable String heading;
    private boolean headingKey;
    private List<Line> lines = new ArrayList<>();
    private int count;
    private boolean truncated;

    public ReportBuilder(String reportId) {
        this.prefix = KEY_ROOT + reportId + ".";
    }

    /** The lang key for {@code name} under this report. */
    public String key(String name) {
        return prefix + name;
    }

    /** Which subject this view is about. */
    public ReportBuilder subject(String id) {
        this.subject = clip(id);
        return this;
    }

    /** Starts a section headed by this report's lang key {@code name}. */
    public ReportBuilder section(String name) {
        flush();
        heading = prefix + name;
        headingKey = true;
        return this;
    }

    /** Starts a section headed by literal text (a player's name, a creature's id). */
    public ReportBuilder literalSection(String text) {
        flush();
        heading = clip(text);
        headingKey = false;
        return this;
    }

    /** Label (this report's lang key {@code name}) and a literal value. */
    public ReportBuilder row(String name, Object value) {
        return add(new Line(prefix + name, true, clip(String.valueOf(value)), false, Tone.NORMAL));
    }

    public ReportBuilder row(String name, Object value, Tone tone) {
        return add(new Line(prefix + name, true, clip(String.valueOf(value)), false, tone));
    }

    /** Label (this report's lang key {@code name}) and a value that is itself a lang key. */
    public ReportBuilder rowKey(String name, String valueKey, Tone tone) {
        return add(new Line(prefix + name, true, valueKey, true, tone));
    }

    /** Label as literal text (a name, an id) and a literal value. */
    public ReportBuilder literal(String label, Object value) {
        return literal(label, value, Tone.NORMAL);
    }

    public ReportBuilder literal(String label, Object value, Tone tone) {
        return add(new Line(clip(label), false, clip(String.valueOf(value)), false, tone));
    }

    /** A sentence on its own: this report's lang key {@code name}. */
    public ReportBuilder note(String name) {
        return add(new Line(prefix + name, true, "", false, Tone.MUTED));
    }

    /** A sentence on its own, literal. */
    public ReportBuilder literalNote(String text, Tone tone) {
        return add(new Line(clip(text), false, "", false, tone));
    }

    /** Yes/no as the shared lang values, coloured {@code good} when it is the healthy answer. */
    public ReportBuilder flag(String name, boolean value, boolean healthyWhenTrue) {
        return rowKey(name, KEY_ROOT + (value ? "yes" : "no"), value == healthyWhenTrue ? Tone.GOOD : Tone.WARN);
    }

    public ReportBuilder subjectOption(String id, String label, String note) {
        if (subjects.size() < MAX_SUBJECTS) {
            subjects.add(new Subject(clip(id), clip(label), clip(note)));
        }
        return this;
    }

    /** A button for the current subject. {@code labelName} is this report's lang key. */
    public ReportBuilder action(String operation, String labelName, String argument, boolean destructive) {
        if (actions.size() < MAX_ACTIONS) {
            actions.add(new Action(operation, prefix + labelName, subject, clip(argument), destructive));
        }
        return this;
    }

    /** A button for an explicit subject (a "reset all" on a page with nothing picked). */
    public ReportBuilder actionOn(String operation, String labelName, String target, String argument, boolean destructive) {
        if (actions.size() < MAX_ACTIONS) {
            actions.add(new Action(operation, prefix + labelName, clip(target), clip(argument), destructive));
        }
        return this;
    }

    public boolean full() {
        return count >= MAX_LINES;
    }

    private ReportBuilder add(Line line) {
        if (count >= MAX_LINES) {
            truncated = true;
            return this;
        }
        if (heading == null) {
            heading = "";
            headingKey = false;
        }
        lines.add(line);
        count++;
        return this;
    }

    private void flush() {
        if (heading != null && !lines.isEmpty()) {
            sections.add(new Section(heading, headingKey, lines));
        } else if (heading != null && !heading.isEmpty()) {
            // An empty section still shows its heading with a "nothing here" line, so empty is not mistaken for missing.
            sections.add(new Section(heading, headingKey, List.of(new Line(KEY_ROOT + "none", true, "", false, Tone.MUTED))));
        }
        heading = null;
        lines = new ArrayList<>();
    }

    public ReportView build() {
        flush();
        if (truncated) {
            sections.add(new Section(KEY_ROOT + "truncated_heading", true,
                    List.of(new Line(KEY_ROOT + "truncated", true, "", false, Tone.WARN))));
            truncated = false;
        }
        return new ReportView(subject, sections, subjects, actions);
    }

    private static String clip(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 1) + "…";
    }
}
