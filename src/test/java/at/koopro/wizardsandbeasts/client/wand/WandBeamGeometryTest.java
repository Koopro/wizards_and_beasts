package at.koopro.wizardsandbeasts.client.wand;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Properties of the procedural beam path that are checkable without a render context. The look has to
 * be judged in-game, but these three invariants are the ones whose breakage is invisible in a
 * screenshot and obvious in motion: a beam that leaves its endpoints, a shape that swims between
 * render passes, and a morphing style that pops at every keyframe.
 */
class WandBeamGeometryTest {

    private static final Vec3 START = new Vec3(10.0, 64.5, -20.0);
    private static final Vec3 END = new Vec3(28.0, 71.0, -4.5);
    private static final double EPS = 1e-9;

    private static BeamStyle style() {
        return BeamStyles.ARC.withHue(0xFF00FF00);
    }

    @Test
    void path_pinsBothEndpointsToTheRay() {
        WandBeamGeometry.Bolt bolt = WandBeamGeometry.buildBolt(START, END, 1234L, 5678L, 0.5f, style());

        assertNotNull(bolt);
        Vec3[] core = bolt.core();
        assertTrue(core.length > 2, "beam should be subdivided into multiple segments");
        assertEquals(0.0, core[0].distanceTo(START), EPS, "beam must start at the wand tip");
        assertEquals(0.0, core[core.length - 1].distanceTo(END), EPS, "beam must end on the target");
    }

    @Test
    void path_isDeterministicForTheSameSeeds() {
        WandBeamGeometry.Bolt first = WandBeamGeometry.buildBolt(START, END, 42L, 43L, 0.3f, style());
        WandBeamGeometry.Bolt second = WandBeamGeometry.buildBolt(START, END, 42L, 43L, 0.3f, style());

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.core().length, second.core().length);
        for (int i = 0; i < first.core().length; i++) {
            assertEquals(0.0, first.core()[i].distanceTo(second.core()[i]), EPS,
                    "node " + i + " must not move between render passes");
        }
    }

    @Test
    void fullyMorphedFrame_matchesTheNextKeyframesOwnShape() {
        // The arc's shape at the end of keyframe A must be the shape keyframe B starts from, or a
        // morphing style jumps every time the cadence rolls over. (Forks are rebuilt from the leaving
        // keyframe's stream and do snap, by design — only the arc is compared.)
        WandBeamGeometry.Bolt arriving = WandBeamGeometry.buildBolt(START, END, 7L, 8L, 1.0f, style());
        WandBeamGeometry.Bolt next = WandBeamGeometry.buildBolt(START, END, 8L, 9L, 0.0f, style());

        assertNotNull(arriving);
        assertNotNull(next);
        assertEquals(arriving.core().length, next.core().length);
        for (int i = 0; i < arriving.core().length; i++) {
            assertEquals(0.0, arriving.core()[i].distanceTo(next.core()[i]), 1e-6,
                    "node " + i + " must line up across the keyframe boundary");
        }
    }

    @Test
    void jaggedNodes_stayWithinTheStylesDisplacementBudget() {
        BeamStyle style = style();
        WandBeamGeometry.Bolt bolt = WandBeamGeometry.buildBolt(START, END, 99L, 100L, 0.5f, style);

        assertNotNull(bolt);
        // Node offset is capped at jag, the whole-arc bow at jag * bow; anything beyond that and the
        // beam would visibly miss what the server says it hit.
        double budget = style.path().jag() * (1.0 + style.path().bow()) + EPS;
        Vec3 axis = END.subtract(START).normalize();
        for (Vec3 node : bolt.core()) {
            Vec3 fromStart = node.subtract(START);
            double lateral = fromStart.subtract(axis.scale(fromStart.dot(axis))).length();
            assertTrue(lateral <= budget,
                    "node strayed " + lateral + " blocks off the ray, budget is " + budget);
        }
    }

    @Test
    void forkCount_respectsTheStyleBudget() {
        BeamStyle style = style();
        WandBeamGeometry.Bolt bolt = WandBeamGeometry.buildBolt(START, END, 5L, 6L, 0.0f, style);

        assertNotNull(bolt);
        assertTrue(bolt.forks().size() <= style.path().maxForks(),
                "fork budget exceeded: " + bolt.forks().size());
    }

    @Test
    void forksDisabled_producesNoBranches() {
        BeamStyle noForks = style().cappedForks(0);
        WandBeamGeometry.Bolt bolt = WandBeamGeometry.buildBolt(START, END, 5L, 6L, 0.0f, noForks);

        assertNotNull(bolt);
        assertTrue(bolt.forks().isEmpty());
    }

    @Test
    void degenerateBeam_isSkipped() {
        assertNull(WandBeamGeometry.buildBolt(START, START.add(0.01, 0, 0), 1L, 2L, 0f, style()));
    }
}
