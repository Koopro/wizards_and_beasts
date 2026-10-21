package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generator's promises: every path it hands out is fair, the same seed always gives the same path,
 * paths actually vary, and the fallback is never needed in practice but always works.
 */
class TracePathGeneratorTest {

    private static final int SEEDS = 2_000;

    @Test
    void everyGeneratedPathIsFair() {
        for (TraceDifficulty d : TraceDifficulty.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                long s = seed;
                TracePath path = TracePathGenerator.generate(s, d);
                String problem = TracePathValidator.problem(path, d);
                assertNull(problem, () -> d + " seed " + s + " produced an unfair path: " + problem);
            }
        }
    }

    @Test
    void sameSeedSamePath() {
        for (TraceDifficulty d : TraceDifficulty.values()) {
            TracePath a = TracePathGenerator.generate(424242L, d);
            TracePath b = TracePathGenerator.generate(424242L, d);
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.x(i), b.x(i));
                assertEquals(a.y(i), b.y(i));
            }
        }
    }

    @Test
    void pathsVaryAcrossSeeds() {
        for (TraceDifficulty d : TraceDifficulty.values()) {
            Set<String> shapes = new HashSet<>();
            for (long seed = 0; seed < 200; seed++) {
                TracePath p = TracePathGenerator.generate(seed, d);
                // Start, goal and midpoint at a coarse grid: two paths that agree on all three look alike.
                double[] mid = p.pointAt(p.length() / 2);
                shapes.add(cell(p.startX(), p.startY()) + cell(p.goalX(), p.goalY()) + cell(mid[0], mid[1]));
            }
            int distinct = shapes.size();
            assertTrue(distinct >= 60, () -> d + " produced only " + distinct + " distinct shapes in 200 seeds");
        }
    }

    @Test
    void fallbackIsRarelyNeededAndAlwaysFair() {
        for (TraceDifficulty d : TraceDifficulty.values()) {
            assertNull(TracePathValidator.problem(TracePathGenerator.fallback(d), d), () -> d + " fallback unfair");
            int fellBack = 0;
            TracePath fallback = TracePathGenerator.fallback(d);
            for (long seed = 0; seed < SEEDS; seed++) {
                TracePath p = TracePathGenerator.generate(seed, d);
                if (p.size() == fallback.size() && p.x(0) == fallback.x(0) && p.y(0) == fallback.y(0)) {
                    fellBack++;
                }
            }
            int finalFellBack = fellBack;
            assertTrue(fellBack * 100 < SEEDS, () -> d + " fell back " + finalFellBack + "/" + SEEDS + " times");
        }
    }

    @Test
    void everyTemplateInEveryPoolCanProduceAFairPath() {
        for (TraceDifficulty d : TraceDifficulty.values()) {
            Map<TraceTemplate, Integer> fair = new EnumMap<>(TraceTemplate.class);
            for (TraceTemplate t : d.templates()) {
                for (int seed = 0; seed < 400; seed++) {
                    Random r = new Random(seed);
                    TracePath p = TracePathGenerator.build(t.raw(r), r.nextInt(4), r.nextBoolean(), r.nextBoolean(), d);
                    if (TracePathValidator.isFair(p, d)) {
                        fair.merge(t, 1, Integer::sum);
                    }
                }
            }
            for (TraceTemplate t : d.templates()) {
                int n = fair.getOrDefault(t, 0);
                assertTrue(n >= 40, () -> d + "/" + t + " is fair in only " + n + " of 400 draws");
            }
        }
    }

    @Test
    void requiredPointerSpeedStaysHumane() {
        // A careful hand traces ~0.6 canvas widths per second; every row's clock must allow half of that,
        // and the longest path of every row must get more time than the shortest.
        for (TraceDifficulty d : TraceDifficulty.values()) {
            assertTrue(d.pace() <= 0.3, () -> d + " asks for " + d.pace() + " widths/s");
            assertTrue(d.timeLimitTicks(d.maxLength()) > d.timeLimitTicks(d.minLength()));
        }
    }

    private static String cell(double x, double y) {
        return (int) (x * 8) + "," + (int) (y * 8) + ";";
    }
}
