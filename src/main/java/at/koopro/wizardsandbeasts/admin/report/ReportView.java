package at.koopro.wizardsandbeasts.admin.report;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What one {@link AdminReport} read, as plain data: the same value the Control Center draws and
 * {@code /wandb admin report} prints. Built on the server by a {@link ReportBuilder}; the client only lays it out.
 *
 * @param subject  the subject this view is about ({@code ""} when the report takes none or none was picked)
 * @param sections headed groups of label/value lines
 * @param subjects what the report can be pointed at (players, creatures nearby …); the page offers a picker
 * @param actions  one-shot operations the page offers as buttons for the current subject
 */
@NullMarked
public record ReportView(String subject, List<Section> sections, List<Subject> subjects, List<Action> actions) {

    public static final ReportView EMPTY = new ReportView("", List.of(), List.of(), List.of());

    public ReportView {
        sections = List.copyOf(sections);
        subjects = List.copyOf(subjects);
        actions = List.copyOf(actions);
    }

    /** How a line's value should read: plain, good news, worth a look, wrong, or background. */
    public enum Tone {
        NORMAL, GOOD, WARN, BAD, MUTED
    }

    /** A heading ({@code headingKey} says whether it is a lang key) and its lines. */
    public record Section(String heading, boolean headingKey, List<Line> lines) {
        public Section {
            lines = List.copyOf(lines);
        }
    }

    /**
     * One line. A line with an empty {@code value} is a sentence on its own (a note); with a value it is a
     * label/value row.
     */
    public record Line(String label, boolean labelKey, String value, boolean valueKey, Tone tone) {}

    /** Something the report can be pointed at. {@code note} is a quiet hint shown beside it. */
    public record Subject(String id, String label, String note) {}

    /**
     * A button for the current subject: runs operation {@code operation} on {@code subject} with {@code argument}.
     * The server decides again whether it may run and whether it needs confirmation; {@code destructive} only says
     * the page should ask first.
     */
    public record Action(String operation, String labelKey, String subject, String argument, boolean destructive) {}

    /**
     * The view with only the lines whose label or value contains {@code needle} (case-insensitive), dropping
     * sections left empty. A section whose heading matches keeps all its lines. Labels are compared as sent (a lang
     * key compares by its key), so the client filters on resolved text instead; this is the command's filter.
     */
    public ReportView filtered(String needle) {
        String n = needle.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) {
            return this;
        }
        List<Section> kept = new ArrayList<>();
        for (Section section : sections) {
            if (section.heading().toLowerCase(Locale.ROOT).contains(n)) {
                kept.add(section);
                continue;
            }
            List<Line> lines = section.lines().stream()
                    .filter(line -> line.label().toLowerCase(Locale.ROOT).contains(n)
                            || line.value().toLowerCase(Locale.ROOT).contains(n))
                    .toList();
            if (!lines.isEmpty()) {
                kept.add(new Section(section.heading(), section.headingKey(), lines));
            }
        }
        return new ReportView(subject, kept, subjects, actions);
    }

    /** Every line, across sections. */
    public int lineCount() {
        return sections.stream().mapToInt(section -> section.lines().size()).sum();
    }
}
