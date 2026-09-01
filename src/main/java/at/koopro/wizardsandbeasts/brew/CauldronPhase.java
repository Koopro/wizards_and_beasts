package at.koopro.wizardsandbeasts.brew;

import net.minecraft.util.StringRepresentable;

/**
 * What a cauldron is doing.
 *
 * <p>Four states rather than a pair of booleans, because the interesting transitions are between
 * named things and a boolean pair can express states that do not exist ("done and spoiled"). The
 * phase is what every interaction on the block branches on, so it has to be the thing that is stored
 * rather than something derived from a timer — a cauldron with {@code remainingTicks == 0} is
 * {@link #DONE} only if it ever started, and {@link #IDLE} otherwise.
 */
public enum CauldronPhase implements StringRepresentable {

    /** Accepting ingredients. Nothing on the heat. */
    IDLE,

    /** A recipe is running. Ingredients are gone, the clock is counting. */
    BREWING,

    /** Finished and waiting to be bottled. Holds its brew until somebody comes with a bottle. */
    DONE,

    /** Left off the heat too long. The batch is ruined and has to be emptied before reuse. */
    SPOILED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Whether a cauldron in this phase will take another ingredient. */
    public boolean acceptsIngredients() {
        return this == IDLE;
    }

    /** Whether the clock should be running. */
    public boolean isActive() {
        return this == BREWING;
    }

    public static CauldronPhase byName(String name, CauldronPhase fallback) {
        for (CauldronPhase phase : values()) {
            if (phase.getSerializedName().equals(name)) {
                return phase;
            }
        }
        return fallback;
    }
}
