package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.CreatureMissile;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.CombatRhythm;
import at.koopro.wizardsandbeasts.entity.creature.ai.CreatureCombatGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.projectile.arrow.Arrow;

/**
 * Combat identity (BR-4, {@code documentation/BEAST_RENAISSANCE.md} §4.7) in a running world: a declared combat
 * profile is fought as declared, and physical missiles fly and land. The beat itself, tick by tick, is in
 * {@code CombatRhythmTest}.
 */
public final class CombatIdentityTests {

    private CombatIdentityTests() {}

    /**
     * Every scene here is built this far above the test ground. Scenarios share the ground horizontally and keep
     * apart by height (see {@code CreatureArena}); at ground level a spell or a flame raised here reached creatures
     * in other scenarios, and their creatures were seen from here, which failed a different test each run.
     */
    private static final int HEIGHT = 230;

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, HEIGHT + y, z);
    }

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("combat_troll_shows_its_blow_before_it_lands",
                "combat: a troll stops and winds up, and the blow lands only when the windup is over",
                CombatIdentityTests::trollShowsItsBlow, 400);
        tests.addAlone("combat_a_shown_blow_can_be_stepped_out_of",
                "combat: a target that leaves during a troll's windup is not hurt",
                CombatIdentityTests::shownBlowCanBeDodged, 400);
        tests.addAlone("combat_prey_is_struck_in_the_same_rhythm",
                "combat: a troll hunting a cow (prey, fought by the ecology goal) also winds up before it hits",
                CombatIdentityTests::preyIsStruckInRhythm, 500);
        tests.addAlone("combat_dugbog_lies_still_until_stepped_on",
                "combat: a dugbog with a target far off does not move; brought close, it bites",
                CombatIdentityTests::dugbogLiesInWait, 500);
        tests.addAlone("combat_centaur_shoots_and_keeps_its_distance",
                "combat: a centaur hurts its target with an arrow and does not close with it",
                CombatIdentityTests::centaurShootsFromRange, 600);
        tests.addAlone("combat_giant_throws_a_boulder_from_afar",
                "combat: a giant throws a boulder at a distant target before it reaches it",
                CombatIdentityTests::giantThrowsABoulder, 500);
        tests.addAlone("combat_a_caster_without_a_profile_still_closes",
                "combat: an obscurus (hex bolt, vanilla melee, no profile) closes with its target instead of standing off",
                CombatIdentityTests::casterStillCloses, 500);
        tests.addAlone("combat_red_cap_is_driven_off_by_a_spell",
                "combat: a red cap closing on its target runs when a spell is cast beside it",
                helper -> drivenOff(helper, "red_cap", true), 500);
        tests.addAlone("combat_yeti_runs_from_fire",
                "combat: a yeti closing on its target runs when a flame is lit beside it",
                helper -> drivenOff(helper, "yeti", false), 500);
        tests.addAlone("combat_lobalug_spits_only_when_struck",
                "combat: a lobalug left alone sends nothing; struck, it spits at whoever struck it",
                CombatIdentityTests::lobalugSpitsWhenStruck, 300);
    }

    /**
     * Removes anything still in the air from the scenario before. The scenes stand side by side at one height and
     * run one after another; an arrow or a boulder loosed in the last tick of one scenario once flew on and struck
     * the next one's target, which read as a troll hitting with no windup.
     */
    private static void sweepMissiles(GameTestHelper helper) {
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(helper.absolutePos(at(0, 0, 0)))
                .inflate(96.0, 48.0, 96.0);
        helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, around)
                .forEach(net.minecraft.world.entity.Entity::discard);
    }

    private static GenericBeastEntity beast(FlightFrameworkTests.Scene scene, String id, BlockPos pos) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
        return (GenericBeastEntity) scene.spawn(type, pos);
    }

    private static Cow dummy(FlightFrameworkTests.Scene scene, BlockPos pos) {
        Cow cow = scene.spawn(EntityType.COW, pos);
        cow.setNoAi(true);
        return cow;
    }

    /**
     * A target no ecology profile lists as prey. A cow is prey to a troll or a dugbog, and prey is hunted by the
     * ecology goal with its own bite, which is not the fight these scenarios are about.
     */
    private static IronGolem foe(FlightFrameworkTests.Scene scene, BlockPos pos) {
        IronGolem golem = scene.spawn(EntityType.IRON_GOLEM, pos);
        golem.setNoAi(true);
        return golem;
    }

    private static CombatRhythm.Phase phase(GenericBeastEntity beast) {
        return beast.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(CreatureCombatGoal.class::isInstance)
                .map(goal -> ((CreatureCombatGoal) goal).phase())
                .findFirst().orElse(null);
    }

    /** Sets the quarry and reports whether it held: a target in a chunk still coming up does not stick. */
    private static boolean aimAt(GenericBeastEntity beast, LivingEntity cow) {
        if (beast.getTarget() != cow) {
            beast.setTarget(cow);
        }
        return beast.getTarget() == cow;
    }

    private static void trollShowsItsBlow(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-8, 1, -8), at(20, 1, 16));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(0, 0, 12, 8, HEIGHT + 1);
        GenericBeastEntity troll = beast(scene, "troll", at(3, 2, 4));
        IronGolem cow = foe(scene, at(7, 2, 4));
        float full = cow.getHealth();
        int[] tick = {0};
        int[] windupAt = {-1};
        helper.onEachTick(() -> {
            if (!aimAt(troll, cow)) {
                return;
            }
            tick[0]++;
            if (phase(troll) == null) {
                scene.finish("a troll with a combat profile has no CreatureCombatGoal");
                return;
            }
            if (windupAt[0] < 0 && phase(troll) == CombatRhythm.Phase.WINDUP) {
                windupAt[0] = tick[0];
            }
            if (cow.getHealth() < full) {
                if (windupAt[0] < 0) {
                    scene.finish("the troll's blow landed with no windup shown");
                } else if (tick[0] - windupAt[0] < 14) {
                    scene.finish("the blow landed " + (tick[0] - windupAt[0]) + " ticks into a 16-tick windup");
                } else {
                    scene.finish(null);
                }
            } else if (tick[0] > 300) {
                scene.finish("the troll never hurt a cow four blocks away (phase " + phase(troll) + ")");
            }
        });
    }

    private static void shownBlowCanBeDodged(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-8, 1, -8), at(28, 1, 16));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(0, 0, 22, 8, HEIGHT + 1);
        GenericBeastEntity troll = beast(scene, "troll", at(3, 2, 4));
        IronGolem cow = foe(scene, at(7, 2, 4));
        float full = cow.getHealth();
        int[] tick = {0};
        int[] movedAt = {-1};
        helper.onEachTick(() -> {
            if (!aimAt(troll, cow)) {
                return;
            }
            tick[0]++;
            if (movedAt[0] < 0) {
                if (phase(troll) == CombatRhythm.Phase.WINDUP) {
                    BlockPos far = helper.absolutePos(at(19, 2, 4));
                    cow.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
                    movedAt[0] = tick[0];
                } else if (cow.getHealth() < full) {
                    scene.finish("the cow was hurt before any windup");
                } else if (tick[0] > 300) {
                    scene.finish("the troll never wound up (phase " + phase(troll) + ")");
                }
                return;
            }
            if (cow.getHealth() < full) {
                scene.finish("the cow was hurt " + (tick[0] - movedAt[0]) + " ticks after it left the troll's reach");
            } else if (tick[0] - movedAt[0] > 25) {
                // The windup has run out and its blow met nothing; the troll is in its recovery.
                scene.finish(null);
            }
        });
    }

    private static void preyIsStruckInRhythm(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-8, 1, -8), at(20, 1, 16));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(0, 0, 12, 8, HEIGHT + 1);
        GenericBeastEntity troll = beast(scene, "troll", at(3, 2, 4));
        Cow cow = dummy(scene, at(7, 2, 4));
        float full = cow.getHealth();
        int[] tick = {0};
        int[] inReachAt = {-1};
        helper.onEachTick(() -> {
            if (!aimAt(troll, cow)) {
                return;
            }
            tick[0]++;
            // The reach both the ecology hunt and the combat goal strike from, at its widest.
            double reach = troll.getBbWidth() * 2.0 + cow.getBbWidth();
            if (inReachAt[0] < 0 && troll.distanceTo(cow) <= reach) {
                inReachAt[0] = tick[0];
            }
            if (cow.getHealth() < full) {
                int waited = inReachAt[0] < 0 ? -1 : tick[0] - inReachAt[0];
                scene.finish(waited >= 14 ? null
                        : "the cow was hurt " + waited + " ticks after the troll came into reach: no 16-tick windup");
            } else if (tick[0] > 400) {
                scene.finish("the troll never hurt the cow (in reach at tick " + inReachAt[0] + ")");
            }
        });
    }

    private static void dugbogLiesInWait(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-8, 1, -8), at(24, 1, 16));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(0, 0, 16, 8, HEIGHT + 1);
        GenericBeastEntity dugbog = beast(scene, "dugbog", at(3, 2, 4));
        IronGolem cow = foe(scene, at(11, 2, 4));
        float full = cow.getHealth();
        int[] tick = {0};
        double[] startX = {Double.NaN};
        helper.onEachTick(() -> {
            if (!aimAt(dugbog, cow)) {
                return;
            }
            tick[0]++;
            if (tick[0] <= 20) {
                // One second to land and to finish whatever step it was taking when it noticed its target
                // (one run in twelve it was still sliding at tick 17).
                startX[0] = dugbog.getX();
                return;
            }
            if (tick[0] <= 60) {
                if (Math.abs(dugbog.getX() - startX[0]) > 0.75) {
                    scene.finish("the dugbog crept " + Math.abs(dugbog.getX() - startX[0])
                            + " blocks toward a target eight blocks off instead of lying still");
                } else if (tick[0] == 60) {
                    cow.teleportTo(dugbog.getX() + 2.0, dugbog.getY(), dugbog.getZ());
                }
                return;
            }
            if (tick[0] > 60) {
                if (cow.getHealth() < full) {
                    scene.finish(null);
                } else if (tick[0] > 260) {
                    scene.finish("the dugbog never bit a cow two blocks away (phase " + phase(dugbog) + ")");
                }
            }
        });
    }

    private static void centaurShootsFromRange(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-24, 1, -24), at(48, 1, 40));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(-10, -10, 30, 26, HEIGHT + 1);
        GenericBeastEntity centaur = beast(scene, "centaur", at(4, 2, 8));
        IronGolem cow = foe(scene, at(12, 2, 8));
        float full = cow.getHealth();
        int[] tick = {0};
        boolean[] arrowSeen = {false};
        StringBuilder trace = new StringBuilder(" trace");
        double[] nearest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (!aimAt(centaur, cow)) {
                return;
            }
            tick[0]++;
            nearest[0] = Math.min(nearest[0], centaur.distanceTo(cow));
            if (tick[0] % 10 == 0) {
                trace.append(String.format(" %d:%.1f/%s", tick[0], centaur.distanceTo(cow), phase(centaur)));
                centaur.goalSelector.getAvailableGoals().stream().filter(w -> w.isRunning())
                        .forEach(w -> trace.append('+').append(w.getGoal().getClass().getSimpleName()));
                trace.append(String.format(" c(%.1f,%.1f) t(%.1f,%.1f) hp%.0f", centaur.getX() - cow.getX(),
                        centaur.getZ() - cow.getZ(), cow.getX(), cow.getZ(), cow.getHealth()));
            }
            arrowSeen[0] |= !helper.getLevel().getEntitiesOfClass(Arrow.class,
                    centaur.getBoundingBox().inflate(24.0), arrow -> arrow.getOwner() == centaur).isEmpty();
            if (tick[0] < 160) {
                return;   // give it time to take up its distance: it starts two blocks inside it
            }
            if (centaur.distanceTo(cow) < 7.0) {
                scene.finish("after eight seconds the centaur stood " + centaur.distanceTo(cow)
                        + " blocks from its target, not near its ten (phase " + phase(centaur) + ", nav done "
                        + centaur.getNavigation().isDone() + ")" + trace);
            } else if (cow.getHealth() < full) {
                if (!arrowSeen[0]) {
                    scene.finish("the cow was hurt but no arrow of the centaur's was ever in the air");
                } else if (nearest[0] < 3.0) {
                    scene.finish("the centaur closed to " + nearest[0] + " blocks instead of keeping its distance");
                } else {
                    scene.finish(null);
                }
            } else if (tick[0] > 500) {
                scene.finish("no arrow hit in 25 seconds (arrow seen " + arrowSeen[0] + ", nearest " + nearest[0] + ")");
            }
        });
    }

    private static void giantThrowsABoulder(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-16, 1, -16), at(40, 1, 24));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(-2, -2, 26, 12, HEIGHT + 1);
        GenericBeastEntity giant = beast(scene, "giant", at(3, 2, 5));
        IronGolem foe = foe(scene, at(19, 2, 5));
        float full = foe.getHealth();
        int[] tick = {0};
        boolean[] boulderSeen = {false};
        boolean[] boulderHit = {false};
        double[] start = {-1.0};
        helper.onEachTick(() -> {
            if (!aimAt(giant, foe)) {
                return;
            }
            tick[0]++;
            double gap = giant.distanceTo(foe);
            if (start[0] < 0) {
                start[0] = gap;
            }
            boulderSeen[0] |= !helper.getLevel().getEntitiesOfClass(CreatureMissile.class,
                    giant.getBoundingBox().inflate(32.0), CreatureMissile::isBoulder).isEmpty();
            if (!boulderHit[0] && foe.getHealth() < full) {
                if (!boulderSeen[0]) {
                    scene.finish("the foe was hurt with no boulder thrown (gap " + gap + ")");
                    return;
                }
                if (gap < 5.0) {
                    scene.finish("the foe was first hurt at arm's length, not by the boulder (gap " + gap + ")");
                    return;
                }
                boulderHit[0] = true;
            }
            // It throws on the way in; it does not stand and throw. The ranged goal once held it where it stood.
            if (boulderHit[0] && gap < start[0] - 6.0) {
                scene.finish(null);
            } else if (tick[0] > 420) {
                scene.finish("boulder hit " + boulderHit[0] + " (seen " + boulderSeen[0] + "), and the giant went from "
                        + start[0] + " to " + gap + " blocks: it must both hit from afar and walk in");
            }
        });
    }

    private static void casterStillCloses(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-8, 1, -8), at(28, 1, 16));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(0, 0, 20, 8, HEIGHT + 1);
        GenericBeastEntity obscurus = beast(scene, "obscurus", at(3, 3, 4));
        IronGolem foe = foe(scene, at(12, 2, 4));
        int[] tick = {0};
        double[] start = {-1.0};
        helper.onEachTick(() -> {
            if (!aimAt(obscurus, foe)) {
                return;
            }
            tick[0]++;
            double gap = obscurus.distanceTo(foe);
            if (start[0] < 0) {
                start[0] = gap;
            }
            // Its bolt reaches twelve blocks, so it is in range from the first tick. Before the melee goal gave up
            // the gaze, the bolt goal held it and the creature never moved.
            if (gap < 3.5) {
                scene.finish(null);
            } else if (tick[0] > 400) {
                scene.finish("the obscurus went from " + start[0] + " to " + gap + " blocks in twenty seconds");
            }
        });
    }

    /**
     * A creature with a canon weakness is on its way to its target; the thing it fears happens between them; it
     * must drop the fight and get well away. {@code spell} raises a cast, otherwise a flame.
     */
    private static void drivenOff(GameTestHelper helper, String id, boolean spell) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-24, 1, -24), at(40, 1, 32));
        // Seven blocks of ground, not a sheet in the sky: a creature that flees picks random spots a few blocks up
        // and down as well as away, and over a one-block floor nearly all of them are air (three runs in eight it
        // found nowhere to run to and stood where it was).
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper);
        for (int layer = -5; layer <= 1; layer++) {
            scene.floor(-16, -12, 30, 20, HEIGHT + layer);
        }
        GenericBeastEntity beast = beast(scene, id, at(3, 2, 4));
        IronGolem foe = foe(scene, at(9, 2, 4));
        int[] tick = {0};
        boolean[] frightened = {false};
        double[] furthest = {0.0};
        StringBuilder trace = new StringBuilder(" trace");
        net.minecraft.world.phys.Vec3[] fright = {null};
        helper.onEachTick(() -> {
            // Under a full suite the chunk can take seconds to start ticking its entities; a creature that is not
            // ticking cannot be frightened. The clock starts when it has lived a few ticks.
            if (beast.tickCount < 5 || (!frightened[0] && !aimAt(beast, foe))) {
                return;
            }
            tick[0]++;
            if (tick[0] == 20) {
                net.minecraft.world.phys.Vec3 between = beast.position().lerp(foe.position(), 0.5);
                fright[0] = between;
                if (spell) {
                    at.koopro.wizardsandbeasts.creature.ecology.CreatureStimuli.spellCast(helper.getLevel(), between);
                } else {
                    // A real flame, announced the way flint and steel announces it: the listener must find the
                    // fire block at the event's position by itself.
                    BlockPos lit = at(6, 2, 4);
                    scene.stone(lit);   // registers the position for clean-up
                    helper.setBlock(lit, net.minecraft.world.level.block.Blocks.FIRE);
                    helper.getLevel().gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE,
                            helper.absolutePos(lit));
                }
                frightened[0] = true;   // from here the target is the creature's own business
                return;
            }
            if (tick[0] < 20) {
                return;
            }
            // What it runs from is the fright, three blocks in front of it, not its old target.
            double gap = beast.position().distanceTo(fright[0]);
            furthest[0] = Math.max(furthest[0], gap);
            if (tick[0] % 20 == 0) {
                trace.append(' ').append(tick[0]).append(':')
                        .append(at.koopro.wizardsandbeasts.creature.ecology.EcologySensing.state(beast).intent());
                beast.goalSelector.getAvailableGoals().stream().filter(w -> w.isRunning())
                        .forEach(w -> trace.append('+').append(w.getGoal().getClass().getSimpleName()));
            }
            if (gap > 7.0 && beast.getTarget() == null) {
                scene.finish(null);
            } else if (tick[0] > 320) {
                scene.finish("in fifteen seconds the " + id + " got no further than " + furthest[0]
                        + " blocks from what frightened it, starting at three (target "
                        + (beast.getTarget() == null ? "dropped" : "kept") + "); ecology intent "
                        + at.koopro.wizardsandbeasts.creature.ecology.EcologySensing.state(beast).intent()
                        + trace);
            }
        });
    }

    private static void lobalugSpitsWhenStruck(GameTestHelper helper) {
        sweepMissiles(helper);
        WizardTestSupport.forceChunks(helper, at(-8, 1, -8), at(16, 1, 16));
        FlightFrameworkTests.Scene scene = new FlightFrameworkTests.Scene(helper).floor(0, 0, 8, 8, HEIGHT + 1);
        GenericBeastEntity lobalug = beast(scene, "lobalug", at(2, 2, 4));
        Cow cow = dummy(scene, at(5, 2, 4));
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            boolean spit = !helper.getLevel().getEntitiesOfClass(CreatureMissile.class,
                    lobalug.getBoundingBox().inflate(12.0), missile -> !missile.isBoulder()).isEmpty();
            if (tick[0] < 40) {
                if (spit) {
                    scene.finish("a lobalug nobody touched spat");
                }
                return;
            }
            if (tick[0] == 40) {
                lobalug.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(cow), 1.0f);
                return;
            }
            if (spit) {
                scene.finish(null);
            } else if (tick[0] > 60) {
                scene.finish("a struck lobalug did not spit at its attacker");
            }
        });
    }
}
