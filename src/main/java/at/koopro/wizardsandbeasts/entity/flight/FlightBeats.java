package at.koopro.wizardsandbeasts.entity.flight;

import net.minecraft.world.entity.Entity;

import java.util.function.Consumer;

/**
 * Takeoff and landing, read off the server's own ground state.
 *
 * <p>A winged walker's wings have to answer to leaving and touching the ground whether the AI flew it or a rider did,
 * and the rider's client does the flying: the only thing both cases share on the server is {@code onGround()}, which
 * the rider's movement packets keep current. So the beat is detected there, once, and played to everyone through
 * GeckoLib's synced trigger. The caller passes its trigger; this holds the two numbers it needs.
 */
public final class FlightBeats {

    /** Airborne at least this long before touching down counts as a landing rather than a hop. */
    private static final int LANDING_AIR_TICKS = 15;
    /**
     * A ridden creature still climbing this long after leaving the ground is flying, not jumping: a jump has peaked by
     * then. Leaving the ground alone used to play the takeoff on every one-block step-up (Beast Renaissance D13).
     */
    static final int RIDDEN_TAKEOFF_TICKS = 8;

    private boolean wasOnGround = true;
    private boolean wasFlying;
    private boolean tookOff;
    private int airTicks;

    /** Call once per server tick; fires {@code "takeoff"}, {@code "land"} or {@code "flap"} (a hard ridden climb). */
    public void update(Entity creature, Consumer<String> trigger) {
        boolean ground = creature.onGround();
        double climb = creature.getDeltaMovement().y;
        boolean flying = creature instanceof WingedWalker walker && walker.isFlying();
        int air = ground ? 0 : airTicks + 1;
        if (takesOff(flying, wasFlying, creature.isVehicle(), ground, air, climb, tookOff)) {
            trigger.accept("takeoff");
            tookOff = true;
        } else if (!wasOnGround && ground && airTicks > LANDING_AIR_TICKS) {
            trigger.accept("land");
        } else if (!ground && creature.isVehicle() && climb > 0.15 && creature.tickCount % 20 == 0) {
            trigger.accept("flap");
        }
        if (ground) {
            tookOff = false;
        }
        airTicks = air;
        wasOnGround = ground;
        wasFlying = flying;
    }

    /**
     * The takeoff beat: its own AI chose to fly this tick, or a rider has kept it climbing past a jump. Never a hop.
     */
    static boolean takesOff(boolean flying, boolean wasFlying, boolean ridden, boolean onGround, int airTicks,
                            double climb, boolean alreadyTookOff) {
        if (alreadyTookOff) {
            return false;
        }
        if (flying && !wasFlying) {
            return true;
        }
        return ridden && !onGround && airTicks == RIDDEN_TAKEOFF_TICKS && climb > 0.05;
    }
}
