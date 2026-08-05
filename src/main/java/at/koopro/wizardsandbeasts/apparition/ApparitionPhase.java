package at.koopro.wizardsandbeasts.apparition;

import org.jspecify.annotations.NullMarked;

/**
 * Where an attempt is in the Three Ds.
 *
 * <p>Destination is not a phase. The destination raycast runs every tick of {@link #DETERMINATION} and
 * {@link #DELIBERATION} alike and only <i>resolves</i> at release, so modelling it as a period the player
 * passes through would misdescribe it. What the presentation layer actually needs to distinguish is
 * "still holding" from "the window is open", which is exactly the two phases below.
 */
@NullMarked
public enum ApparitionPhase {

    /** No attempt in progress. */
    IDLE,

    /** Holding, before the window opens. Destabilization accrues here. */
    DETERMINATION,

    /** The window is open; releasing now is clean. */
    DELIBERATION,

    /** Released or discharged; the outcome is being applied. */
    RESOLVING
}
