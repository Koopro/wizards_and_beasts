package at.koopro.wizardsandbeasts.form;

/**
 * Defines the physical dimensions and attribute modifiers for a player form.
 * <p>
 * {@code hitboxWidth} and {@code hitboxHeight} are the intended collision box size in blocks.
 * {@code hitboxHeight} drives {@code Attributes.SCALE} (scale = hitboxHeight / 1.8).
 * {@code modelScale} is the visual-only size multiplier — may differ from hitbox scale.
 * {@code modelAspectX} and {@code modelAspectZ} are visual width/depth ratios relative to
 * {@code modelScale} (1.0 = proportional; >1 = wider/deeper than tall).
 */
public record SizeProfile(
        String id,
        float hitboxWidth,
        float hitboxHeight,
        float modelScale,
        float modelAspectX,
        float modelAspectZ,
        float reachBonus,
        float knockbackResistance,
        float stepHeight
) {

    private static final float DEFAULT_PLAYER_WIDTH  = 0.6f;
    private static final float DEFAULT_PLAYER_HEIGHT = 1.8f;

    /** Standard human profile — no modifications. */
    public static final SizeProfile DEFAULT = new SizeProfile(
            "default",
            DEFAULT_PLAYER_WIDTH, DEFAULT_PLAYER_HEIGHT,
            1.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 0.0f);

    /**
     * Returns true if this profile has non-uniform visual shape
     * (modelAspectX or modelAspectZ differ from 1.0), requiring
     * client-side PoseStack aspect-ratio correction.
     */
    public boolean isNonUniform() {
        return Math.abs(modelAspectX - 1.0f) > 0.001f
                || Math.abs(modelAspectZ - 1.0f) > 0.001f;
    }

    /**
     * Value to pass to an {@code ADD_MULTIPLIED_BASE} AttributeModifier on
     * {@code Attributes.SCALE}. Derived from hitboxHeight so the actual
     * collision box matches the declared dimensions.
     */
    public double scaleAttributeValue() {
        return (hitboxHeight / DEFAULT_PLAYER_HEIGHT) - 1.0;
    }
}
