package at.koopro.wizardsandbeasts.entity.flight;

import net.minecraft.world.phys.Vec3;

/**
 * A creature that lives on the ground and flies when it chooses to, and can be ridden in both — the Hippogriff
 * and the Thestral.
 *
 * <p>The shape shared by {@link WingedWalkerMoveControl} (vanilla's ground control, or its flying control while
 * {@link #isFlying()}), {@link WingedWalkerFlightGoal} (bounded flights when nobody rides it) and
 * {@link RiddenFlight} (vanilla's ridden contract, as the Happy Ghast flies). Implementors are
 * {@link net.minecraft.world.entity.PathfinderMob}s; the flag is server state, and animation reads the synced
 * ground state instead of it.
 */
public interface WingedWalker {

    /** True while the unridden AI is flying; false on the ground and under a rider. */
    boolean isFlying();

    void setFlying(boolean flying);

    /** Whether it may take off on its own just now — not while it is busy with something else. */
    default boolean canTakeFlight() {
        return true;
    }

    /** Lowest cruising height over the terrain, in blocks. */
    default int cruiseAltitude() {
        return 6;
    }

    /** How far above {@link #cruiseAltitude()} a waypoint may sit. */
    default int cruiseAltitudeSpan() {
        return 7;
    }

    /** How far a single leg of a flight reaches, in blocks. */
    default double flightReach() {
        return 18.0;
    }

    /** Whether it meets every fight in the air, however near the target stands (the thunderbird and its storm). */
    default boolean fightsFromTheAir() {
        return false;
    }

    /** Whether it lands by choice on the highest ground it can find (griffin, thunderbird) rather than anywhere. */
    default boolean perches() {
        return false;
    }

    /** Vanilla's {@code travelFlying} for a ridden flight; it is protected, so the creature calls it. */
    void flyRidden(Vec3 input, float speed);
}
