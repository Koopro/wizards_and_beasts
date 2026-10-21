package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.creature.ecology.EcologyGoal;
import at.koopro.wizardsandbeasts.creature.ecology.EcologySensing;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The creature AI scheduling tiers against the live server (documentation/CREATURE_AI_SCHEDULING.md): an event is
 * answered within a few ticks instead of at the next scheduled look, a chase re-paths at vanilla's pace and still
 * catches, a camouflaged creature stays continuously invisible on the cheaper refresh, and a death nobody can care
 * about costs no query.
 */
final class CreatureAiScheduleTests {

    private static final int Y = 2;

    private CreatureAiScheduleTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("creature_ai_event_wakes_within_a_few_ticks",
                "creature AI: a unicorn hurt just after a look runs within a few ticks, not at its next look",
                CreatureAiScheduleTests::eventWake);
        tests.addAlone("creature_ai_pursuit_is_paced",
                "creature AI: a hunting hippogriff asks for a path at most every few ticks while chasing",
                CreatureAiScheduleTests::pursuitPaced);
        tests.addAlone("creature_ai_flying_predator_chases_by_sight",
                "creature AI: a dragon that can see a sheep flies straight at it and eats, with almost no path searches",
                CreatureAiScheduleTests::flyingChase);
        tests.addAlone("creature_ai_large_flier_still_wanders",
                "creature AI: a dragon left alone takes off and flies somewhere, by straight collision-checked flight",
                CreatureAiScheduleTests::largeFlierWanders);
        tests.addAlone("creature_ai_camouflage_stays_continuous",
                "creature AI: a moke is invisible on every tick, though its effect is refreshed only twice a second",
                CreatureAiScheduleTests::camouflageContinuous);
        tests.addAlone("creature_ai_irrelevant_death_costs_no_query",
                "creature AI: a zombie's death raises no stimulus query; a unicorn's does",
                CreatureAiScheduleTests::irrelevantDeath);
    }

    private static boolean fleeing(PathfinderMob mob) {
        return EcologySensing.state(mob).intent().name().startsWith("FLEE");
    }

    /**
     * Running from the blow by either path: the ecology's flight, or vanilla's panic (priority 1, which a passive
     * creature may start first when it finds a spot to bolt to — it then holds MOVE, and the ecology goal waits).
     */
    private static boolean answered(PathfinderMob mob) {
        return fleeing(mob) || mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.isRunning() && g.getGoal() instanceof net.minecraft.world.entity.ai.goal.PanicGoal);
    }

    private static void eventWake(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob unicorn = arena.creature("unicorn", 1, 1);
        unicorn.setNoAi(false);
        int[] tick = {0};
        int[] hurtAt = {-1};
        long[] looksAtHurt = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (hurtAt[0] < 0) {
                // Hurt it two ticks after one of its scheduled looks: the next one is eighteen ticks away, so a flight
                // inside a few ticks can only be the event's doing.
                if (tick[0] > AiCadence.MEDIUM.period() + 2
                        && AiCadence.MEDIUM.due(unicorn.tickCount - 2, unicorn.getId())) {
                    check(helper, !answered(unicorn), () -> "setup: already fleeing");
                    looksAtHurt[0] = AiMetrics.get(AiMetrics.Metric.ECOLOGY_LOOK);
                    boolean hit = unicorn.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0f);
                    check(helper, hit, () -> "setup: the blow did not land");
                    hurtAt[0] = tick[0];
                }
                return;
            }
            int elapsed = tick[0] - hurtAt[0];
            if (answered(unicorn)) {
                check(helper, elapsed <= EcologyGoal.EVENT_MIN_GAP + 3,
                        () -> "the unicorn fled " + elapsed + " ticks after the blow");
                arena.clear();
                helper.succeed();
            } else if (elapsed > AiCadence.MEDIUM.period() - 4) {
                arena.clear();
                helper.fail("a hurt unicorn did not answer the blow before its next scheduled look ("
                        + EcologySensing.state(unicorn).intent() + " after " + elapsed + " ticks; awake="
                        + EcologySensing.state(unicorn).awake() + ", looks since=" + (AiMetrics.get(
                        AiMetrics.Metric.ECOLOGY_LOOK) - looksAtHurt[0]) + ", perceived "
                        + EcologySensing.state(unicorn).perception() + ")");
            }
        });
    }

    private static void pursuitPaced(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob hippogriff = arena.creature("hippogriff", 1, 1);
        hippogriff.setNoAi(false);
        Chicken chicken = arena.spawn(EntityType.CHICKEN, 9, 1);
        long[] before = {0};
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 1) {
                before[0] = AiMetrics.get(AiMetrics.Metric.PATH_REQUEST);
            }
            if (chicken.isAlive() && tick[0] % 3 == 0) {
                // Keep the prey moving a block at a time, so an unpaced chase would re-path every few ticks.
                chicken.setPos(chicken.getX() + (tick[0] % 6 == 0 ? 1 : -1), chicken.getY(), chicken.getZ());
            }
            if (!chicken.isAlive() || tick[0] >= 200) {
                long paths = AiMetrics.get(AiMetrics.Metric.PATH_REQUEST) - before[0];
                int chased = tick[0];
                arena.clear();
                check(helper, paths <= chased / 4 + 2, () -> paths + " paths in " + chased + " ticks of chasing");
                check(helper, paths >= 1, () -> "the hippogriff never chased");
                helper.succeed();
            }
        });
    }

    private static void flyingChase(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y + 6);
        PathfinderMob dragon = arena.creature("common_welsh_green", 1, 1);
        dragon.setNoAi(false);
        var sheep = arena.spawn(EntityType.SHEEP, 14, 1);
        long[] before = {0};
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 1) {
                before[0] = AiMetrics.get(AiMetrics.Metric.PATH_REQUEST);
            }
            if (!sheep.isAlive()) {
                long paths = AiMetrics.get(AiMetrics.Metric.PATH_REQUEST) - before[0];
                int took = tick[0];
                arena.clear();
                // Path *requests* still count the pursuit's paced checks; what a direct chase saves is the search.
                check(helper, paths <= took / 4 + 2, () -> paths + " path checks in " + took + " ticks");
                helper.succeed();
            } else if (tick[0] > 400) {
                arena.clear();
                helper.fail("the dragon did not catch a sheep it could see in 400 ticks (intent "
                        + EcologySensing.state(dragon).intent() + ")");
            }
        });
    }

    private static void largeFlierWanders(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y + 12);
        PathfinderMob dragon = arena.creature("hungarian_horntail", 4, 1);
        dragon.setNoAi(false);
        dragon.setNoGravity(false);
        check(helper, dragon.goalSelector.getAvailableGoals().stream()
                        .anyMatch(g -> g.getGoal() instanceof at.koopro.wizardsandbeasts.entity.creature.ai.DirectFlightWanderGoal),
                () -> "a dragon is not on the direct-flight wander");
        net.minecraft.world.phys.Vec3 start = dragon.position();
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (dragon.position().distanceTo(start) > 4.0) {
                arena.clear();
                helper.succeed();
            } else if (tick[0] > 1200) {
                arena.clear();
                helper.fail("a dragon left alone for a minute never wandered");
            }
        });
    }

    private static void camouflageContinuous(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        PathfinderMob moke = arena.creature("moke", 1, 1);
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] < 3) {
                return;
            }
            check(helper, moke.hasEffect(MobEffects.INVISIBILITY), () -> "the moke was visible on tick " + tick[0]);
            if (tick[0] >= 120) {
                arena.clear();
                helper.succeed();
            }
        });
    }

    private static void irrelevantDeath(GameTestHelper helper) {
        CreatureArena arena = CreatureArena.at(helper, Y);
        try {
            Zombie zombie = arena.spawn(EntityType.ZOMBIE, 3, 3);
            long queries = AiMetrics.get(AiMetrics.Metric.STIMULUS_QUERY);
            long skipped = AiMetrics.get(AiMetrics.Metric.STIMULUS_SKIPPED);
            zombie.kill(helper.getLevel());
            check(helper, AiMetrics.get(AiMetrics.Metric.STIMULUS_QUERY) == queries
                            && AiMetrics.get(AiMetrics.Metric.STIMULUS_SKIPPED) == skipped + 1,
                    () -> "a zombie's death ran a stimulus query");
            PathfinderMob unicorn = arena.creature("unicorn", 1, 1);
            unicorn.kill(helper.getLevel());
            check(helper, AiMetrics.get(AiMetrics.Metric.STIMULUS_QUERY) > queries,
                    () -> "a unicorn's death raised no stimulus for its herd");
            helper.succeed();
        } finally {
            arena.clear();
        }
    }
}
