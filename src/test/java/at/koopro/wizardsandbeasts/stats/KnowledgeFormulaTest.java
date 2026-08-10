package at.koopro.wizardsandbeasts.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the weights and bounds of the one derived stat.
 *
 * <p>KNOWLEDGE is never stored or trained — {@code PlayerStatsAPI.computeKnowledge} recomputes it
 * from four attachments on every read. That makes the weighting the whole feature, and it is
 * unguarded arithmetic that four unrelated systems feed.
 */
class KnowledgeFormulaTest {

    @Test
    void eachSourceContributesItsDeclaredWeight() {
        assertEquals(2, KnowledgeFormula.compute(1, 0, 0, 0), "one spell");
        assertEquals(1, KnowledgeFormula.compute(0, 1, 0, 0), "one bestiary entry");
        assertEquals(3, KnowledgeFormula.compute(0, 0, 1, 0), "one skill node");
        assertEquals(2, KnowledgeFormula.compute(0, 0, 0, 1), "one lore book");
    }

    @Test
    void sourcesSum() {
        assertEquals(8, KnowledgeFormula.compute(1, 1, 1, 1));
        assertEquals(24, KnowledgeFormula.compute(3, 2, 4, 2));
    }

    @Test
    void aPlayerWhoHasLearnedNothingScoresZero() {
        assertEquals(0, KnowledgeFormula.compute(0, 0, 0, 0));
    }

    @Test
    void theResultIsClampedToTheStatRange() {
        assertEquals(100, KnowledgeFormula.compute(500, 500, 500, 500));
        // Negative inputs cannot occur today, but the clamp is what keeps a future counter bug from
        // producing a negative stat that the GUI would paint as an inverted bar.
        assertEquals(0, KnowledgeFormula.compute(-10, 0, 0, 0));
    }

    @Test
    void deliberateProgressionOutweighsPassiveSighting() {
        // Unlocking a skill node is a spent point; discovering a beast is walking past one. The
        // ordering is the design statement, so it is asserted rather than left to the constants.
        assertTrue(KnowledgeFormula.WEIGHT_SKILL_NODE > KnowledgeFormula.WEIGHT_SPELL);
        assertTrue(KnowledgeFormula.WEIGHT_SPELL > KnowledgeFormula.WEIGHT_BESTIARY);
        assertEquals(KnowledgeFormula.WEIGHT_SPELL, KnowledgeFormula.WEIGHT_BOOK);
    }

    @Test
    void knowledgeIsDerivedNotTrainable() {
        assertTrue(PlayerStat.KNOWLEDGE.isDerived());
        assertTrue(!PlayerStat.KNOWLEDGE.isTrainable(),
                "a trainable derived stat would let addTrainingProgress write a value that the next"
                        + " recompute silently discards");
    }
}
