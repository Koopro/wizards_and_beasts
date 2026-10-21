package at.koopro.wizardsandbeasts.creature.schedule;

import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduling tiers' arithmetic (documentation/CREATURE_AI_SCHEDULING.md): each tier runs exactly as often as it
 * says, a herd spreads its work over the period, tiers checked from goals land on goal-evaluation ticks, a failing
 * search backs off, a pursuit re-paths at vanilla's pace, and the scratch list is never handed out twice.
 */
class AiScheduleTest {

    private static int runs(AiCadence cadence, int entityId, int ticks) {
        int n = 0;
        for (int t = 0; t < ticks; t++) {
            n += cadence.due(t, entityId) ? 1 : 0;
        }
        return n;
    }

    @Test
    void eachTierRunsAsOftenAsItSays() {
        for (int id = 0; id < 50; id++) {
            assertEquals(2000, runs(AiCadence.FAST, id, 2000));
            assertEquals(100, runs(AiCadence.MEDIUM, id, 2000));
            assertEquals(20, runs(AiCadence.SLOW, id, 2000));
            assertEquals(0, runs(AiCadence.EVENT, id, 2000), "EVENT never runs on a clock");
        }
    }

    @Test
    void aHerdSpreadsItsSensingOverThePeriod() {
        // Twenty creatures spawned together (consecutive ids, same tickCount): one looks around per tick, never all
        // twenty on one tick — that spike is what `tickCount % 20` produced.
        int period = AiCadence.MEDIUM.period();
        for (int t = 0; t < 200; t++) {
            int due = 0;
            for (int id = 1000; id < 1000 + period; id++) {
                due += AiCadence.MEDIUM.due(t, id) ? 1 : 0;
            }
            assertEquals(1, due, "tick " + t);
        }
    }

    @Test
    void clockedTiersLandOnGoalEvaluationTicks() {
        // Vanilla evaluates goals only when (tickCount + id) is even; a tier checked from canUse must fall there.
        for (AiCadence cadence : List.of(AiCadence.MEDIUM, AiCadence.SLOW)) {
            for (int id = 0; id < 40; id++) {
                for (int t = 0; t < 400; t++) {
                    if (cadence.due(t, id)) {
                        assertEquals(0, (t + id) % 2, cadence + " due on an odd tick");
                    }
                }
            }
        }
    }

    @Test
    void slowNeverLandsOnTheSameTickAsMedium() {
        for (int id = 0; id < 40; id++) {
            for (int t = 0; t < 1000; t++) {
                assertFalse(AiCadence.MEDIUM.due(t, id) && AiCadence.SLOW.due(t, id), "id " + id + " tick " + t);
            }
        }
    }

    @Test
    void everyIsStaggeredAndOutOfPhaseWithSensing() {
        for (int period : new int[]{10, 20, 30, 40}) {
            int n = 0;
            for (int t = 0; t < period * 50; t++) {
                n += AiCadence.every(t, 7, period) ? 1 : 0;
            }
            assertEquals(50, n, "period " + period);
        }
        for (int t = 0; t < 400; t++) {
            assertFalse(AiCadence.every(t, 3, 20) && AiCadence.MEDIUM.due(t, 3), "a 20-tick ability beat and sensing");
        }
        assertTrue(AiCadence.every(5, 9, 1));
    }

    @Test
    void aFailingSearchBacksOffAndASuccessResets() {
        AiBackoff backoff = new AiBackoff(20, 100);
        assertTrue(backoff.ready(0));
        backoff.fail(0);
        assertFalse(backoff.ready(19));
        assertTrue(backoff.ready(20));
        backoff.fail(20);
        assertEquals(60, backoff.readyAt(), "second failure waits twice as long");
        backoff.fail(60);
        backoff.fail(140);
        backoff.fail(240);
        assertEquals(340, backoff.readyAt(), "capped at the maximum");
        for (int i = 0; i < 100; i++) {
            backoff.fail(1000);
        }
        assertEquals(1100, backoff.readyAt(), "never overflows past the cap");
        backoff.succeed();
        assertTrue(backoff.ready(1001));
        assertEquals(0, backoff.failures());
    }

    @Test
    void pursuitWaitsLikeVanilla() {
        for (int id = 0; id < 14; id++) {
            int near = AiPursuit.delay(4 * 4, id);
            assertTrue(near >= AiPursuit.MIN_DELAY && near <= AiPursuit.MIN_DELAY + 6, "near " + near);
            assertEquals(near + 5, AiPursuit.delay(20 * 20, id));
            assertEquals(near + 10, AiPursuit.delay(40 * 40, id));
        }
    }

    @Test
    void theScratchListIsNeverHandedOutTwice() {
        List<Entity> first = AiScratch.borrow();
        List<Entity> nested = AiScratch.borrow();
        assertNotSame(first, nested, "a nested borrow gets its own list");
        AiScratch.release(nested);
        AiScratch.release(first);
        List<Entity> again = AiScratch.borrow();
        assertSame(first, again, "the released list is reused");
        assertTrue(again.isEmpty(), "and handed back empty");
        AiScratch.release(again);
    }

    @Test
    void metricsCount() {
        long before = AiMetrics.get(AiMetrics.Metric.BACKOFF_SKIP);
        AiMetrics.inc(AiMetrics.Metric.BACKOFF_SKIP);
        assertEquals(before + 1, AiMetrics.get(AiMetrics.Metric.BACKOFF_SKIP));
        assertTrue(AiMetrics.summary().contains("backoff_skip="));
    }
}
