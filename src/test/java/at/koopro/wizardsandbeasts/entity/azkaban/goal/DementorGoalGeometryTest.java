package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.entity.azkaban.DementorAura;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the goals send a Dementor: away from the light, close but not into its victim. */
class DementorGoalGeometryTest {

    @Test
    void itFleesStraightAwayFromThePatronusAndOutOfItsWard() {
        Vec3 dementor = new Vec3(10, 64, 0);
        Vec3 patronus = new Vec3(4, 64, 0);
        double ward = 12;
        Vec3 to = FleeFromPatronusGoal.fleePoint(dementor, patronus, ward);
        assertTrue(to.x > dementor.x, "it fled toward the Patronus: " + to);
        assertEquals(0.0, to.z, 1e-9, "it fled sideways: " + to);
        assertTrue(to.y > dementor.y, "it did not rise: " + to);
        double out = Math.hypot(to.x - patronus.x, to.z - patronus.z);
        assertTrue(out > ward, "it stopped inside the ward: " + out);
    }

    @Test
    void aDementorOnTopOfThePatronusStillGetsADirection() {
        Vec3 here = new Vec3(0, 64, 0);
        Vec3 to = FleeFromPatronusGoal.fleePoint(here, here, 5);
        assertTrue(Math.hypot(to.x, to.z) > 5, "no way out when standing on the Patronus: " + to);
    }

    @Test
    void itHangsBeforeItsVictimWithinTheDrainAndNeverOnThem() {
        Vec3 victim = new Vec3(0, 64, 0);
        for (Vec3 from : new Vec3[]{new Vec3(20, 70, 0), new Vec3(-3, 64, 4), new Vec3(0, 64, 0)}) {
            Vec3 hover = DementorPursueGoal.hoverPoint(from, victim);
            double flat = Math.hypot(hover.x - victim.x, hover.z - victim.z);
            assertEquals(DementorPursueGoal.HOVER_DISTANCE, flat, 1e-9, "hover from " + from);
            assertTrue(flat < DementorAura.DRAIN_RADIUS, "hovering outside the drain");
        }
    }

    @Test
    void theKissClosesInsideItsReach() {
        Vec3 victim = new Vec3(0, 64, 0);
        Vec3 close = DementorKissGoal.closeIn(new Vec3(3, 64, 0), victim);
        assertTrue(close.distanceTo(victim) < DementorKissGoal.KISS_REACH, "the windup ends out of reach: " + close);
        assertTrue(DementorKissGoal.KISS_REACH < DementorPursueGoal.HOVER_DISTANCE,
                "a hovering Dementor could Kiss without closing in");
    }
}
