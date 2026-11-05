package at.koopro.wizardsandbeasts.entity.beast.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When the Hidebehind stops stalking and strikes (Beast Renaissance D3): within reach of a back turned to it. It used
 * to stalk across its whole follow range, so its melee goal never ran.
 */
class HidebehindStalkGoalTest {

    private static final Vec3 PLAYER = new Vec3(0, 64, 0);
    private static final Vec3 NORTH = new Vec3(0, 0, -1);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);

    @Test
    void behindATurnedBackWithinReachItStrikes() {
        Vec3 behind = new Vec3(0, 64, 2);
        assertTrue(HidebehindStalkGoal.inStrikePosition(behind, PLAYER, NORTH, behind.distanceToSqr(PLAYER)));
    }

    @Test
    void facedItKeepsStalking() {
        Vec3 inFront = new Vec3(0, 64, 2);
        assertFalse(HidebehindStalkGoal.inStrikePosition(inFront, PLAYER, SOUTH, inFront.distanceToSqr(PLAYER)));
    }

    @Test
    void outOfReachItKeepsStalking() {
        Vec3 far = new Vec3(0, 64, 6);
        assertFalse(HidebehindStalkGoal.inStrikePosition(far, PLAYER, NORTH, far.distanceToSqr(PLAYER)));
    }
}
