package at.koopro.wizardsandbeasts.entity.broom.handling;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;

/**
 * Training brooms — the Cleansweep Seven and the generic starter.
 *
 * <p>Forgiving in every direction that matters to somebody learning: it will not run away with you
 * under boost, it stops when you let go, it banks less alarmingly, and putting it into a wall costs
 * you less than it would on anything else. What it gives up is precision — it wanders, and it
 * wanders worse the harder you push it.
 */
public final class SchoolHandling implements BroomHandlingProfile {

    /**
     * The most boost a school broom will actually deliver, whatever its JSON claims.
     *
     * <p>A cap rather than a retune of the shipped values: at 1.30 the Cleansweep is already under
     * it, so this changes nothing about any broom in the game today. It exists so a datapack cannot
     * hand a training broom a Firebolt's 2.5 and have it behave like one — the school profile is a
     * promise about how the broom treats a student, and a promise a number can override is not one.
     */
    private static final float BOOST_CEILING = 1.35f;

    /** Extra bite on the brakes with nothing held. Students need to be able to stop. */
    private static final float STOPPING_BONUS = 1.15f;

    /** How much of the bank is taken off. A gentler lean reads as a steadier broom. */
    private static final float ROLL_DAMPING = 0.80f;

    @Override
    public String profileId() {
        return "school";
    }

    @Override
    public float modifyTargetSpeed(float targetSpeed, BroomEntity broom, BroomDefinition def,
                                   boolean boosting) {
        if (!boosting || def.boostMultiplier() <= BOOST_CEILING) {
            return targetSpeed;
        }
        // Undo the definition's multiplier and reapply the capped one, rather than clamping the
        // speed itself: the target has already been through the config multiplier, the skill bonus
        // and the Snidget feather, and clamping here would quietly cancel all three.
        return targetSpeed / def.boostMultiplier() * BOOST_CEILING;
    }

    @Override
    public float modifyDeceleration(float deceleration, BroomEntity broom, BroomDefinition def) {
        return BroomHandlingProfile.super.modifyDeceleration(deceleration, broom, def) * STOPPING_BONUS;
    }

    @Override
    public float modifyRollTilt(float rollTilt, BroomEntity broom, BroomDefinition def) {
        return rollTilt * ROLL_DAMPING;
    }

    /**
     * A lurch when the boost catches, because a training broom is not built for it.
     *
     * <p>The one place a school broom feels worse rather than safer, and deliberately so: the profile
     * caps how much speed the boost delivers, so without something to mark the moment, boosting a
     * Cleansweep would read as the key doing nothing. This is the broom complaining.
     *
     * <p>Applied to vertical velocity at the top of the tick, so the vertical lerp later in the same
     * tick damps most of it back out — a kick that settles, not a jump.
     */
    @Override
    public void onBoostStart(BroomEntity broom, BroomDefinition def) {
        broom.setVerticalVelocity(broom.getVerticalVelocity() + BOOST_LURCH);
    }

    /** Blocks per tick of upward kick when the boost catches. Small: this is a nudge, not a launch. */
    private static final float BOOST_LURCH = 0.06f;
}
