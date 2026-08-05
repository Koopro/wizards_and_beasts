package at.koopro.wizardsandbeasts.apparition;

import org.jspecify.annotations.NullMarked;

/**
 * The two ways a wizard travels. They differ in what they cost to hold together, not in how they are aimed:
 * both charge, both open the same Deliberation window, both splinch by the same ladder.
 *
 * <p>{@link #ANCHORED} is deliberately legal in combat and practically impossible to finish under fire — a
 * seventy-tick hold that a single hit aborts. That is the balance mechanism; there is no combat lockout.
 */
@NullMarked
public enum ApparitionTier {

    /** Line of sight, combat-viable. A short hold and a short window. */
    BLINK(10, 40, 1.0f, false),

    /** To a memorised destination, any distance, same dimension. A long hold that damage ends. */
    ANCHORED(70, 1200, 6.0f, true);

    /** Blink range at proficiency 0. */
    private static final int BLINK_BASE_RANGE = 12;
    /** Extra blink range earned across the whole proficiency curve. */
    private static final int BLINK_PROFICIENCY_RANGE = 18;

    private final int chargeDurationTicks;
    private final int cooldownTicks;
    private final float exhaustion;
    private final boolean abortsOnDamage;

    ApparitionTier(int chargeDurationTicks, int cooldownTicks, float exhaustion, boolean abortsOnDamage) {
        this.chargeDurationTicks = chargeDurationTicks;
        this.cooldownTicks = cooldownTicks;
        this.exhaustion = exhaustion;
        this.abortsOnDamage = abortsOnDamage;
    }

    /** Ticks of Determination before the Deliberation window opens. */
    public int chargeDurationTicks() {
        return chargeDurationTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** Food exhaustion charged on a completed attempt. */
    public float exhaustion() {
        return exhaustion;
    }

    /**
     * Whether damage during Determination ends the attempt outright rather than merely inflating the miss.
     * True for {@link #ANCHORED} only.
     */
    public boolean abortsOnDamage() {
        return abortsOnDamage;
    }

    public boolean hasRangeLimit() {
        return this == BLINK;
    }

    /**
     * Maximum travel distance in blocks. {@link #ANCHORED} is unbounded within a dimension — the whole point
     * of a remembered destination is that it is far away — so this is only meaningful for {@link #BLINK}.
     */
    public double rangeBlocks(float proficiency) {
        if (!hasRangeLimit()) {
            return Double.MAX_VALUE;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, proficiency));
        return BLINK_BASE_RANGE + (int) Math.floor(clamped * BLINK_PROFICIENCY_RANGE);
    }
}
