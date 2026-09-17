package at.koopro.wizardsandbeasts.wand.allegiance;

/**
 * Where a wand stands with the wizard holding it. Internal vocabulary: players read it as a sentence on the
 * tooltip ("It answers you grudgingly"), never as these names.
 *
 * <ul>
 *   <li>{@link #UNFAMILIAR} — the wand serves someone else, or (the Elder Wand) no one; it works, badly</li>
 *   <li>{@link #RELUCTANT} — its master, but newly won or long neglected</li>
 *   <li>{@link #ACCEPTING} — its master; an ordinary working relationship</li>
 *   <li>{@link #LOYAL} — its master, proven over many casts</li>
 *   <li>{@link #MASTERED} — its master, completely</li>
 * </ul>
 */
public enum WandBondState {
    UNFAMILIAR,
    RELUCTANT,
    ACCEPTING,
    LOYAL,
    MASTERED;

    /** Whether the holder is the wand's master at all. */
    public boolean isMaster() {
        return this != UNFAMILIAR;
    }

    /** Whether the wand has proven itself to its master — the states that unlock loyal behaviour. */
    public boolean isLoyal() {
        return this == LOYAL || this == MASTERED;
    }
}
