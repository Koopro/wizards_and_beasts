package at.koopro.wizardsandbeasts.spell.gamp;

public enum EnforcementType {
    /** Spell is cancelled and no cast cooldown is consumed. */
    HARD_REJECT,
    /** Spell executes but receives a corrupted or substituted result. */
    SOFT_PENALTY
}
