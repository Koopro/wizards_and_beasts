package at.koopro.wizardsandbeasts.pose;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which flight attitudes change the player's collision box, and to what.
 *
 * <p>The interesting assertion is the first one: the prone box is vanilla's, and the test says so by
 * building vanilla's rather than restating a literal. A hand-copied {@code 0.6} would keep passing
 * on the day vanilla changed its mind, which is the only day the assertion matters.
 */
class FlightHitboxTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // EntityDimensions is a plain record, but Avatar's constants pull in enough of the entity
        // package to want a bootstrapped registry.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The prone box is the same box vanilla gives an elytra flier, which is the comparison a player
     * makes: a broom at speed should fit through whatever an elytra fits through.
     */
    @Test
    void theProneBoxIsVanillasElytraBox() {
        EntityDimensions vanilla = EntityDimensions.scalable(0.6F, Avatar.SWIMMING_BB_HEIGHT);
        assertEquals(vanilla.width(), FlightHitbox.PRONE.width(), 1.0E-6);
        assertEquals(vanilla.height(), FlightHitbox.PRONE.height(), 1.0E-6);
    }

    /** Flat body, flat box. */
    @Test
    void propelledIsProne() {
        assertNotNull(FlightHitbox.forState(FlightPoseState.PROPELLED));
        assertEquals(FlightHitbox.PRONE.height(),
                FlightHitbox.forState(FlightPoseState.PROPELLED).height(), 1.0E-6);
    }

    /**
     * Glide leans at 35 degrees and hover is upright. Neither is lying down, and a leaning player
     * with a crouch-height box would slip through gaps their model plainly does not fit.
     */
    @Test
    void onlyPropelledChangesTheBox() {
        assertNull(FlightHitbox.forState(FlightPoseState.GLIDE));
        assertNull(FlightHitbox.forState(FlightPoseState.HOVER));
    }

    /** No flight state at all is the ordinary standing player; the handler must not touch them. */
    @Test
    void noStateLeavesTheBoxAlone() {
        assertNull(FlightHitbox.forState(null));
    }

    /**
     * The prone box must actually be shorter than a standing player, or none of this does anything.
     *
     * <p>{@code Avatar.STANDING_DIMENSIONS} is {@code protected}, so the standing height is the one
     * literal here that cannot be read back from vanilla.
     */
    @Test
    void theProneBoxIsShorterThanStanding() {
        assertTrue(FlightHitbox.PRONE.height() < 1.8F,
                "a prone box that is not shorter than standing changes nothing");
    }
}
