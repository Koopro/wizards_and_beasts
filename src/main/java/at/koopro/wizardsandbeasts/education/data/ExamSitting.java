package at.koopro.wizardsandbeasts.education.data;

import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Qualification;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A sitting in progress at the examination desk. Saved on the candidate, so a relog or a crash mid-paper neither
 * loses the practicals done nor restarts the clock; {@code education.ExamSittings} marks it when the candidate hands
 * in or the clock runs out, whichever is first.
 *
 * @param level        O.W.L. or N.E.W.T.
 * @param startTick    game time the sitting opened
 * @param deadlineTick game time it closes by itself
 * @param papers       the subjects sat, in subject order
 * @param practicals   subjects whose practical was done under the clock
 * @param questions    the written paper, drawn at the start from what the candidate has actually read and charted
 * @param answers      one chosen option per question, empty until the paper is handed in (it is answered once)
 */
@NullMarked
public record ExamSitting(Qualification level, long startTick, long deadlineTick, List<OWLSubject> papers,
                          Set<OWLSubject> practicals, List<PaperQuestion> questions, List<Integer> answers) {

    public static final Codec<ExamSitting> CODEC = RecordCodecBuilder.create(i -> i.group(
            Qualification.CODEC.fieldOf("level").forGetter(ExamSitting::level),
            Codec.LONG.fieldOf("start").forGetter(ExamSitting::startTick),
            Codec.LONG.fieldOf("deadline").forGetter(ExamSitting::deadlineTick),
            OWLSubject.CODEC.listOf().fieldOf("papers").forGetter(ExamSitting::papers),
            OWLSubject.CODEC.listOf().xmap(ExamSitting::subjectSet, List::copyOf)
                    .optionalFieldOf("practicals", Set.of()).forGetter(ExamSitting::practicals),
            PaperQuestion.CODEC.listOf().optionalFieldOf("questions", List.of()).forGetter(ExamSitting::questions),
            Codec.INT.listOf().optionalFieldOf("answers", List.of()).forGetter(ExamSitting::answers)
    ).apply(i, ExamSitting::new));

    public ExamSitting {
        papers = List.copyOf(papers);
        practicals = subjectSet(practicals);
        questions = List.copyOf(questions);
        answers = List.copyOf(answers);
    }

    private static Set<OWLSubject> subjectSet(java.util.Collection<OWLSubject> subjects) {
        return subjects.isEmpty() ? Set.of() : java.util.Collections.unmodifiableSet(EnumSet.copyOf(subjects));
    }

    public boolean sits(OWLSubject subject) {
        return papers.contains(subject);
    }

    public boolean answered() {
        return !answers.isEmpty();
    }

    /** The same sitting with one more practical done. */
    public ExamSitting withPractical(OWLSubject subject) {
        EnumSet<OWLSubject> done = practicals.isEmpty() ? EnumSet.noneOf(OWLSubject.class) : EnumSet.copyOf(practicals);
        done.add(subject);
        return new ExamSitting(level, startTick, deadlineTick, papers, done, questions, answers);
    }

    /** The same sitting with its written paper answered. */
    public ExamSitting withAnswers(List<Integer> chosen) {
        return new ExamSitting(level, startTick, deadlineTick, papers, practicals, questions, chosen);
    }

    /**
     * One multiple-choice question. Every string is a translation key, so the paper reads in the candidate's
     * language; the right answer never leaves the server ({@code EducationView} sends the question and options only).
     *
     * @param subject  the section it belongs to
     * @param question translation key of the question
     * @param arg      translation key of the question's one argument, or empty
     * @param options  translation keys of the choices
     * @param answer   index of the right choice
     */
    public record PaperQuestion(OWLSubject subject, String question, String arg, List<String> options, int answer) {

        public static final Codec<PaperQuestion> CODEC = RecordCodecBuilder.create(i -> i.group(
                OWLSubject.CODEC.fieldOf("subject").forGetter(PaperQuestion::subject),
                Codec.STRING.fieldOf("question").forGetter(PaperQuestion::question),
                Codec.STRING.optionalFieldOf("arg", "").forGetter(PaperQuestion::arg),
                Codec.STRING.listOf().fieldOf("options").forGetter(PaperQuestion::options),
                Codec.INT.fieldOf("answer").forGetter(PaperQuestion::answer)
        ).apply(i, PaperQuestion::new));

        public PaperQuestion {
            options = List.copyOf(options);
        }
    }
}
