package at.koopro.wizardsandbeasts.owl;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/** Transient result of conducting an OWL exam — not persisted. */
public record OWLExamResult(
        @NonNull Map<OWLSubject, OWLGrade> grades,
        @NonNull List<Profession> eligibleProfessions
) {}
