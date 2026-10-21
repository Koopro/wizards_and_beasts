package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.education.data.ExamSitting.PaperQuestion;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The written paper. Pure.
 *
 * <p>A question is only ever set on something the candidate has actually met: History of Magic asks about the tomes
 * they read, Astronomy about the constellations they charted. Each tome now shows three passages when studied
 * ({@code item.lore.LoreTomeItem}), and question <i>n</i> of a tome tests passage <i>n</i>; each constellation's
 * one-line fact is printed when it is charted. A candidate who read and looked has seen every answer — the paper
 * checks that the reading happened, it is not a quiz to grind. At most {@link ExamRules#QUESTIONS_PER_SECTION} per
 * section.
 */
@NullMarked
public final class ExamQuestions {

    /** Passages (and questions) per tome. */
    public static final int PASSAGES = 3;

    /** The right answer's index for each tome's question 0, 1, 2 — varied so "always the first" is never right. */
    private static final Map<String, int[]> TOME_ANSWERS = Map.of(
            "a_history_of_magic", new int[] {1, 0, 2},
            "hogwarts_a_history", new int[] {2, 1, 0},
            "rise_and_fall_of_the_dark_arts", new int[] {0, 2, 1});

    private ExamQuestions() {}

    /** Tomes that carry passages and questions, by lore id (the item's registry path). */
    public static Collection<String> tomes() {
        return TOME_ANSWERS.keySet();
    }

    /** Translation key of passage {@code n} of a tome. */
    public static String passageKey(String tome, int n) {
        return "education.tome." + tome + ".passage." + n;
    }

    /** Question {@code n} of a tome. */
    public static PaperQuestion tomeQuestion(String tome, int n) {
        String base = "education.tome." + tome + ".q" + n;
        return new PaperQuestion(OWLSubject.HISTORY_OF_MAGIC, base, "",
                List.of(base + ".a0", base + ".a1", base + ".a2"), TOME_ANSWERS.get(tome)[n]);
    }

    /** "Which constellation …?" with the charted one among two others, in a shuffled order. */
    public static PaperQuestion constellationQuestion(Astronomy.Constellation answer, Random random) {
        List<Astronomy.Constellation> others = new ArrayList<>(List.of(Astronomy.Constellation.values()));
        others.remove(answer);
        Collections.shuffle(others, random);
        List<Astronomy.Constellation> options = new ArrayList<>(List.of(answer, others.get(0), others.get(1)));
        Collections.shuffle(options, random);
        return new PaperQuestion(OWLSubject.ASTRONOMY, "education.paper.astronomy.which", answer.factKey(),
                options.stream().map(Astronomy.Constellation::nameKey).toList(), options.indexOf(answer));
    }

    /**
     * The written paper for a sitting.
     *
     * @param papers         subjects sat; sections are set only for those among them that are written
     * @param tomesRead      lore ids the candidate studied
     * @param constellations bit set of constellations charted
     */
    public static List<PaperQuestion> draw(Collection<OWLSubject> papers, Collection<String> tomesRead,
                                           int constellations, Random random) {
        List<PaperQuestion> out = new ArrayList<>();
        if (papers.contains(OWLSubject.HISTORY_OF_MAGIC)) {
            List<PaperQuestion> pool = new ArrayList<>();
            for (String tome : tomesRead) {
                if (!TOME_ANSWERS.containsKey(tome)) continue;
                for (int n = 0; n < PASSAGES; n++) pool.add(tomeQuestion(tome, n));
            }
            Collections.shuffle(pool, random);
            out.addAll(pool.subList(0, Math.min(ExamRules.QUESTIONS_PER_SECTION, pool.size())));
        }
        if (papers.contains(OWLSubject.ASTRONOMY)) {
            List<Astronomy.Constellation> charted = new ArrayList<>();
            for (Astronomy.Constellation c : Astronomy.Constellation.values()) {
                if ((constellations & c.bit()) != 0) charted.add(c);
            }
            Collections.shuffle(charted, random);
            for (int i = 0; i < Math.min(ExamRules.QUESTIONS_PER_SECTION, charted.size()); i++) {
                out.add(constellationQuestion(charted.get(i), random));
            }
        }
        return List.copyOf(out);
    }
}
