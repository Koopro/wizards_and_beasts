package at.koopro.wizardsandbeasts.spell.tag;

/**
 * Tags for classifying spell-applied mob effects and related gameplay metadata.
 * Used by datapack or registration metadata in future extensions.
 */
public enum SpellEffectTag {
    /**
     * Applied to MobEffects that cannot be removed by Finite Incantatem.
     * Covers: Unforgivable curse effects, creature-aura effects, and
     * effects that require a specific counter-curse to remove.
     */
    FINITE_IMMUNE,

    /**
     * Reserved for future use: marks effects that cannot be removed
     * by any magical means (only potion/item cures).
     * Do not implement behaviour for this tag in this prompt.
     */
    COUNTER_CURSE_ONLY
}
