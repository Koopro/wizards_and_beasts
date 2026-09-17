package at.koopro.wizardsandbeasts.ministry.trace;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the Ministry does when an incident is put to a name, from least to most: record, warning,
 * investigation, official notice (a summons), Auror involvement. Arrest and trial follow from the last two
 * in time, not on receipt of a report.
 */
@NullMarked
public enum MinistryResponse {

    /** Noted on the file. The Obliviators tidied up; a short notice says so. */
    RECORD,
    /** A warning letter from the Improper Use of Magic Office. */
    WARNING,
    /** An investigation opens. */
    INVESTIGATE,
    /** A hearing is called at once. */
    SUMMON,
    /** Aurors take the case. */
    AURORS;

    /** The case stage this response opens, or {@code null} when a letter is the whole of it. */
    public @Nullable CaseStage stage() {
        return switch (this) {
            case RECORD, WARNING -> null;
            case INVESTIGATE -> CaseStage.INVESTIGATING;
            case SUMMON -> CaseStage.SUMMONED;
            case AURORS -> CaseStage.AURORS_ASSIGNED;
        };
    }
}
