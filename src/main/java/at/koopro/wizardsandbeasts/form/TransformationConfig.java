package at.koopro.wizardsandbeasts.form;

/**
 * Configuration for a transformation transition between two forms.
 *
 * @param durationTicks   how long the transition takes (max 30 ticks = 1.5s)
 * @param screenEffect    visual effect displayed during the transition
 * @param freezeMovement  whether the player is frozen in place during the transition
 */
public record TransformationConfig(
        int durationTicks,
        ScreenEffect screenEffect,
        boolean freezeMovement
) {
    /** Default config used when no specific mapping exists. */
    public static final TransformationConfig DEFAULT =
            new TransformationConfig(10, ScreenEffect.DARK_FADE, true);

    /**
     * Screen effect types displayed to the transforming player.
     * The ordinal is sent over the network via TransitionStartS2CPayload.
     */
    public enum ScreenEffect {
        NONE,
        DARK_FADE,
        PARTICLE_BURST,
        SMOKE_CLOUD;

        private static final ScreenEffect[] VALUES = values();

        public static ScreenEffect fromOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
        }
    }
}
