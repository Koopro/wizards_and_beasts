package at.koopro.wizardsandbeasts.entity.broom.handling;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;

/**
 * Heavy antiques — the Oakshaft 79.
 *
 * <p>Slow to start, slow to stop, slow to turn, and almost impossible to upset. It hangs in the air
 * longer than anything else when you let go, and it shrugs off the knocks that chip a racing broom.
 * The one thing it does not get is immunity: a real crash still costs it, because a broom nothing can
 * hurt is a broom with no reason to fly carefully.
 */
public final class TankHandling implements BroomHandlingProfile {

    /** Acceleration retained beyond whatever the JSON already authored. Heavy things start slowly. */
    private static final float ACCELERATION_PENALTY = 0.85f;
    /** Turn rate retained, always — not only at speed. It is the mass, not the momentum. */
    private static final float TURN_PENALTY = 0.75f;
    /** Sink retained with nothing held. Lower is more float. */
    private static final float GRAVITY_RELIEF = 0.85f;
    /** Share of a glancing knock's durability cost that actually lands. */
    private static final float MINOR_IMPACT_RELIEF = 0.5f;

    @Override
    public String profileId() {
        return "tank";
    }

    @Override
    public float modifyAcceleration(float acceleration, BroomEntity broom, BroomDefinition def,
                                    boolean boosting) {
        return acceleration * ACCELERATION_PENALTY;
    }

    @Override
    public float modifyTurnRate(float baseTurnRate, BroomEntity broom, BroomDefinition def,
                                boolean boosting) {
        return baseTurnRate * TURN_PENALTY;
    }

    @Override
    public float modifyWeakGravity(float weakGravity, BroomEntity broom, BroomDefinition def) {
        return weakGravity * GRAVITY_RELIEF;
    }

    /**
     * Half a knock, rounded down but never to nothing.
     *
     * <p>Floored at 1 rather than allowed to reach zero: a broom that takes literally no damage from
     * glancing hits can be flown into scenery forever, and the durability system stops meaning
     * anything for the one broom in the game with the most of it.
     */
    @Override
    public int minorImpactDurabilityLoss(int base, BroomDefinition def) {
        return base <= 0 ? base : Math.max(1, Math.round(base * MINOR_IMPACT_RELIEF));
    }
}
