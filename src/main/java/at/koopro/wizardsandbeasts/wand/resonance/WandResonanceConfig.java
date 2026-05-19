package at.koopro.wizardsandbeasts.wand.resonance;

/**
 * Tunable weights and thresholds for {@link WandResonanceSystem}. Loaded from datapack JSON.
 */
public record WandResonanceConfig(
        float matchThreshold,
        float refuseThreshold,
        int scanRadius,
        float woodWeight,
        float coreWeight,
        float lengthWeight,
        float flexibilityWeight,
        float corruptionDarkBonusThreshold) {

    public static final WandResonanceConfig DEFAULT = new WandResonanceConfig(
            0.65f,
            0.2f,
            5,
            0.4f,
            0.3f,
            0.15f,
            0.15f,
            0.5f);
}
