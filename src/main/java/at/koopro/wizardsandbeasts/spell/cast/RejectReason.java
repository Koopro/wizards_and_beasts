package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

/**
 * Canonical reject bucket used by cast context layers.
 * This starts intentionally small and can grow as Layer 3/4 land.
 */
public enum RejectReason {
    FORBIDDEN_BY_AFFINITY,
    FORBIDDEN_BY_PROFESSION,
    GAMPS_LAW_HARD_REJECT
}
