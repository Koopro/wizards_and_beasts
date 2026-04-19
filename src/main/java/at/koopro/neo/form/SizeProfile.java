package at.koopro.neo.form;

/**
 * Defines the physical dimensions and attribute modifiers for a player form.
 * <p>
 * {@code scaleY} is the primary driver — it maps to {@code Attributes.SCALE} which
 * automatically adjusts hitbox, eye height, and visual model size. {@code scaleX} and
 * {@code scaleZ} are applied as non-uniform visual adjustments only (via PoseStack).
 */
public record SizeProfile(
        String id,
        float scaleX,
        float scaleY,
        float scaleZ,
        float reachBonus,
        float knockbackResistance,
        float stepHeight
) {

    /** Standard human profile — no modifications. */
    public static final SizeProfile DEFAULT = new SizeProfile(
            "default", 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f);

    /**
     * Returns true if this profile has non-uniform scaling (scaleX or scaleZ differ from scaleY),
     * which requires client-side PoseStack compensation.
     */
    public boolean isNonUniform() {
        return Math.abs(scaleX - scaleY) > 0.001f || Math.abs(scaleZ - scaleY) > 0.001f;
    }

    /**
     * The value to pass to an {@code ADD_MULTIPLIED_BASE} AttributeModifier on {@code Attributes.SCALE}.
     * Base scale is 1.0, so a scaleY of 1.3 yields a modifier value of 0.3.
     */
    public double scaleAttributeValue() {
        return scaleY - 1.0;
    }
}
