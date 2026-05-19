package at.koopro.wizardsandbeasts.spell.proficiency;

import at.koopro.wizardsandbeasts.spell.core.*;

public record SpellScalingProfile(
        float damageMult,
        float cooldownMult,
        float durationMult,
        float controlMult,
        float accuracyMult) {

    public static final SpellScalingProfile DEFAULT = new SpellScalingProfile(1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
}
