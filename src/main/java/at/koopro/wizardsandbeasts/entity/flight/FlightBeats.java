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

    private boolean wasOnGround = true;
    private int airTicks;

    /** Call once per server tick; fires {@code "takeoff"}, {@code "land"} or {@code "flap"} (a hard ridden climb). */
    public void update(Entity creature, Consumer<String> trigger) {
        boolean ground = creature.onGround();
        double climb = creature.getDeltaMovement().y;
        if (wasOnGround && !ground && climb > 0.1) {
            trigger.accept("takeoff");
        } else if (!wasOnGround && ground && airTicks > LANDING_AIR_TICKS) {
            trigger.accept("land");
        } else if (!ground && creature.isVehicle() && climb > 0.15 && creature.tickCount % 20 == 0) {
            trigger.accept("flap");
        }
        airTicks = ground ? 0 : airTicks + 1;
        wasOnGround = ground;
    }
}
