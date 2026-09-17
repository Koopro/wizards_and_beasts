package at.koopro.wizardsandbeasts.form;

import net.minecraft.world.entity.Avatar;

/**
 * Defines the physical dimensions and attribute modifiers for a player form.
 * <p>
 * {@code hitboxWidth} and {@code hitboxHeight} are the intended collision box size in blocks.
 * {@code hitboxHeight} drives {@code Attributes.SCALE} (scale = hitboxHeight / 1.8).
 * {@code modelScale} is the visual-only size multiplier — may differ from hitbox scale.
 * {@code modelAspectX} and {@code modelAspectZ} are visual width/depth ratios relative to
 * {@code modelScale} (1.0 = proportional; >1 = wider/deeper than tall).
 *
 * <h2>Eye height is declared, not derived</h2>
 *
 * <p>{@code eyeHeight} is where the camera sits, in blocks above the feet. It is a field rather
 * than a calculation because {@link net.minecraft.world.entity.EntityDimensions#scalable} puts the
 * eye at a flat {@code height * 0.85}, and that ratio only describes an animal that stands upright.
 * A cat's eyes are most of the way up a 0.7-block body; a beetle's are at the front of a 0.3-block
 * one. Deriving both from the same fraction puts one camera inside the skull and the other in the
 * floor.
 *
 * <p>The nine-argument constructor keeps that vanilla ratio for every form that does not care —
 * the humanoid profiles, and the debug overrides — so declaring an eye height is opt-in and
 * nothing that never had an opinion silently gains one.
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
        float stepHeight,
        float eyeHeight
) {

    private static final float DEFAULT_PLAYER_WIDTH  = 0.6f;
    private static final float DEFAULT_PLAYER_HEIGHT = 1.8f;

    /**
     * The fraction of its height vanilla puts an entity's eye at, from
     * {@code EntityDimensions.defaultEyeHeight}. Reproduced here rather than referenced because the
     * method is private; the value is asserted against real {@code EntityDimensions} in
     * {@code SizeProfileEyeHeightTest}.
     */
    public static final float VANILLA_EYE_RATIO = 0.85f;

    /** Standard human profile — no modifications. */
    public static final SizeProfile DEFAULT = new SizeProfile(
            "default",
            DEFAULT_PLAYER_WIDTH, DEFAULT_PLAYER_HEIGHT,
            1.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 0.0f,
            Avatar.DEFAULT_EYE_HEIGHT);

    /**
     * A profile whose eyes sit where vanilla would put them.
     *
     * <p>Every humanoid form, and every caller that is describing a size rather than a creature.
     * The value it fills in is exactly what {@code EntityDimensions.scalable} would have computed,
     * so a profile built this way behaves as it did before eye height was declarable at all.
     */
    public SizeProfile(String id,
                       float hitboxWidth, float hitboxHeight,
                       float modelScale, float modelAspectX, float modelAspectZ,
                       float reachBonus, float knockbackResistance, float stepHeight) {
        this(id, hitboxWidth, hitboxHeight, modelScale, modelAspectX, modelAspectZ,
                reachBonus, knockbackResistance, stepHeight, hitboxHeight * VANILLA_EYE_RATIO);
    }

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
