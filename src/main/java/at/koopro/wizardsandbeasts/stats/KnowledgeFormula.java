package at.koopro.wizardsandbeasts.stats;

/**
 * Pure derivation of the KNOWLEDGE stat from a player's accumulated magical learning.
 * <p>
 * KNOWLEDGE is not stored or trained directly — it is computed on demand from four sources:
 * spells learned, bestiary entries discovered, skill nodes unlocked, and lore books read.
 * The weighting reflects relative effort: skill nodes (deliberate progression) count most,
 * spells and books next, raw bestiary sightings least. Result is clamped to 0–100.
 */
public final class KnowledgeFormula {

    static final int WEIGHT_SPELL       = 2;
    static final int WEIGHT_BESTIARY    = 1;
    static final int WEIGHT_SKILL_NODE  = 3;
    static final int WEIGHT_BOOK        = 2;

    private KnowledgeFormula() {}

    public static int compute(int spellsLearned, int bestiaryDiscovered, int skillNodesUnlocked, int booksRead) {
        int raw = spellsLearned     * WEIGHT_SPELL
                + bestiaryDiscovered * WEIGHT_BESTIARY
                + skillNodesUnlocked * WEIGHT_SKILL_NODE
                + booksRead          * WEIGHT_BOOK;
        return Math.max(0, Math.min(100, raw));
    }
}
