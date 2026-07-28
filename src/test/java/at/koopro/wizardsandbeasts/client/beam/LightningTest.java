package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Properties of the bolt path that are checkable without a render context. The look has to be judged
 * in-game; these are the ones whose breakage is invisible in a screenshot and obvious in motion — a
 * bolt that misses the wand tip or the target, a shape that swims because it re-rolls between render
 * passes, and jitter that wanders past the budget its style asked for.
 *
 * <p>This replaces {@code WandBeamGeometryTest}, which covered the same invariants on the legacy
 * immediate-mode renderer that the entity-based system has now replaced outright.
 */
class LightningTest {

    private static final Vec3 START = new Vec3(10.0, 64.5, -20.0);
    private static final Vec3 END = new Vec3(28.0, 71.0, -4.5);
    private static final double EPS = 1e-9;

    private static Lightning bolt() {
        return new Lightning(6, 5f, 2);
    }

    @Test
    void path_pinsBothEndpoints() {
        Vec3[] path = bolt().joints(START, END, 1234, 40);

        assertEquals(7, path.length, "a 6-segment bolt has 7 joints");
        assertEquals(0.0, path[0].distanceTo(START), EPS, "bolt must start at the wand tip");
        assertEquals(0.0, path[path.length - 1].distanceTo(END), EPS,
                "bolt must end exactly on the target, where damage resolves");
    }

    @Test
    void path_isDeterministicForTheSameSeedAndTick() {
        Vec3[] first = bolt().joints(START, END, 42, 100);
        Vec3[] second = bolt().joints(START, END, 42, 100);

        for (int i = 0; i < first.length; i++) {
            assertEquals(0.0, first[i].distanceTo(second[i]), EPS,
                    "joint " + i + " must not move between render passes of the same frame");
        }
    }

    @Test
    void path_holdsItsShapeForTheStylesFrequencyThenChanges() {
        Lightning shape = new Lightning(6, 5f, 4);

        // Ticks 40..43 share a seed bucket; 44 starts the next one.
        Vec3[] atStart = shape.joints(START, END, 7, 40);
        for (int tick = 41; tick <= 43; tick++) {
            Vec3[] within = shape.joints(START, END, 7, tick);
            for (int i = 0; i < atStart.length; i++) {
                assertEquals(0.0, atStart[i].distanceTo(within[i]), EPS,
                        "bolt must hold still inside its frequency window; moved at tick " + tick);
            }
        }

        Vec3[] afterRoll = shape.joints(START, END, 7, 44);
        boolean moved = false;
        for (int i = 1; i < atStart.length - 1; i++) {
            if (atStart[i].distanceTo(afterRoll[i]) > 1e-6) {
                moved = true;
                break;
            }
        }
        assertTrue(moved, "bolt must re-roll once the frequency window rolls over");
    }

    @Test
    void interiorJoints_stayWithinTheStylesJitterBudget() {
        float spreadPixels = 5f;
        Lightning shape = new Lightning(8, spreadPixels, 2);
        Vec3[] path = shape.joints(START, END, 99, 12);

        Vec3 step = END.subtract(START).scale(1.0 / 8);
        // Jitter is applied per axis at up to spread/16 blocks, so the worst legal displacement is
        // that budget on all three axes at once.
        double budget = (spreadPixels / 16.0) * Math.sqrt(3.0) + 1e-6;
        for (int i = 1; i < path.length - 1; i++) {
            Vec3 straight = START.add(step.scale(i));
            assertTrue(path[i].distanceTo(straight) <= budget,
                    "joint " + i + " wandered " + path[i].distanceTo(straight)
                            + " blocks off the ray, past the " + budget + " budget");
        }
    }

    @Test
    void differentCastersGetDifferentBolts() {
        Vec3[] a = bolt().joints(START, END, 1, 20);
        Vec3[] b = bolt().joints(START, END, 2, 20);

        assertNotEquals(a[1], b[1],
                "two casters firing at once must not draw the same bolt");
    }

    @Test
    void singleSegmentBolt_isJustTheStraightRay() {
        Vec3[] path = new Lightning(1, 5f, 2).joints(START, END, 5, 5);

        assertEquals(2, path.length);
        assertEquals(0.0, path[0].distanceTo(START), EPS);
        assertEquals(0.0, path[1].distanceTo(END), EPS);
    }
}
