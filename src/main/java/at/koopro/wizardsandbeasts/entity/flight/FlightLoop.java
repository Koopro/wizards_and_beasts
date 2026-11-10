package at.koopro.wizardsandbeasts.entity.flight;

import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;

import java.util.function.Predicate;

/**
 * Which loop a creature that both walks and flies should be playing, from what the client can see of it.
 *
 * <p>The generic fliers used to have one moving loop, {@code fly}, so a dragon crossing a field beat its wings at
 * walking pace with its feet on the grass. On the ground this defers to the creature's {@link CreatureActivity}
 * (sleep, eat, run…) with {@code walk} as the moving loop; in the air it flies, and glides while it is losing height
 * on the move. A rig that lacks a clip gets the nearest one it has, never a name GeckoLib would throw on.
 */
public final class FlightLoop {

    /** Losing height faster than this per tick while travelling is a glide, not a flap. */
    static final double GLIDE_SINK = -0.05;

    private FlightLoop() {}

    /**
     * @param supported  on the ground or in water (the synced ground state)
     * @param moving     moving at all (GeckoLib's test)
     * @param climb      vertical movement this tick; negative is down
     * @param travelling moving across the ground, not only up or down
     * @param activity   what the server says it is doing
     * @param has        whether the rig defines a clip
     */
    public static CreatureActivity.Loop pick(boolean supported, boolean moving, double climb, boolean travelling,
                                             CreatureActivity activity, Predicate<String> has) {
        if (supported) {
            if (has.test("walk")) {
                return activity.loop(moving, has, "walk");
            }
            // No walk on this rig yet: its wings at half speed read better than sliding on the idle.
            CreatureActivity.Loop loop = activity.loop(moving, has, "fly");
            return "fly".equals(loop.clip()) ? new CreatureActivity.Loop("fly", loop.speed() * 0.5) : loop;
        }
        if (climb < GLIDE_SINK && travelling) {
            return has.test("glide") ? new CreatureActivity.Loop("glide", 1.0) : new CreatureActivity.Loop("fly", 0.6);
        }
        return new CreatureActivity.Loop("fly", 1.0);
    }
}
