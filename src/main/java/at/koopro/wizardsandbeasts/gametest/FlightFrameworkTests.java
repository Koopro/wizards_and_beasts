package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.creature.GenericFlyingBeastEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared flight framework for large fliers (BR-1, {@code documentation/BEAST_RENAISSANCE.md} §4.6): dragons,
 * winged horses, the griffin and the rest stand on the ground under gravity, take off, and come down again; and they
 * hunt from the air what they cannot reach on foot. The rules without a world are in {@code FlightFrameworkTest}.
 */
public final class FlightFrameworkTests {

    private FlightFrameworkTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("flight_large_fliers_stand_on_the_ground",
                "flight: a dragon, a winged horse and a griffin stand on the ground with gravity; a doxy does not",
                FlightFrameworkTests::largeFliersStandOnTheGround);
        tests.addAlone("flight_startled_flier_takes_off_and_lands",
                "flight: a granian struck by something it cannot fight takes off, flies, and comes down again",
                FlightFrameworkTests::startledFlierTakesOffAndLands, 2000);
        tests.addAlone("flight_hunter_reaches_a_target_on_a_pillar",
                "flight: a griffin takes to the air to strike a target standing out of reach on a pillar",
                FlightFrameworkTests::hunterReachesATargetOnAPillar, 1200);
        tests.addAlone("flight_thunderbird_storms_only_on_the_wing",
                "flight: a thunderbird takes off for a target at arm's length and calls its storm from the air",
                FlightFrameworkTests::thunderbirdStormsOnlyOnTheWing, 500);
    }

    /**
     * What a scenario put into the world, so it can take it out again. A large flier needs more ground than the
     * empty test structure has, and stone left behind lands in the cells of the scenarios that run after this one
     * (a floor of this class once sat over another test's lava pit and failed it).
     */
    static final class Scene {
        private final GameTestHelper helper;
        private final List<BlockPos> blocks = new ArrayList<>();
        private final List<Entity> entities = new ArrayList<>();

        Scene(GameTestHelper helper) {
            this.helper = helper;
        }

        /** A stone floor at relative height {@code y}. */
        Scene floor(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    stone(new BlockPos(x, y, z));
                }
            }
            return this;
        }

        void stone(BlockPos pos) {
            helper.setBlock(pos, Blocks.STONE);
            blocks.add(pos);
        }

        <E extends Entity> E spawn(EntityType<E> type, BlockPos pos) {
            E entity = helper.spawn(type, pos);
            entities.add(entity);
            return entity;
        }

        GenericFlyingBeastEntity flier(String id, BlockPos pos) {
            return (GenericFlyingBeastEntity) spawn(type(id), pos);
        }

        void clear() {
            entities.forEach(Entity::discard);
            entities.clear();
            blocks.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
            blocks.clear();
        }

        /** Clears the scene, then passes or fails: nothing is left behind either way. */
        void finish(String failure) {
            clear();
            if (failure == null) {
                helper.succeed();
            } else {
                helper.fail(failure);
            }
        }
    }

    private static void largeFliersStandOnTheGround(GameTestHelper helper) {
        WizardTestSupport.forceChunks(helper, new BlockPos(-4, 1, -4), new BlockPos(28, 1, 12));
        Scene scene = new Scene(helper).floor(-3, -3, 27, 11, 1);
        String[] ids = {"hungarian_horntail", "abraxan", "griffin", "thunderbird"};
        GenericFlyingBeastEntity[] large = new GenericFlyingBeastEntity[ids.length];
        for (int i = 0; i < ids.length; i++) {
            large[i] = scene.flier(ids[i], new BlockPos(2 + i * 7, 3, 4));
        }
        GenericFlyingBeastEntity doxy = scene.flier("doxy", new BlockPos(2, 4, 9));
        boolean[] stood = new boolean[ids.length];
        boolean[] floated = new boolean[ids.length];
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (++tick[0] > 60) {
                return;
            }
            for (int i = 0; i < ids.length; i++) {
                // Standing at some point, and never weightless unless it chose to fly.
                stood[i] |= large[i].onGround() && !large[i].isNoGravity();
                floated[i] |= large[i].isNoGravity() && !large[i].isFlying();
            }
            if (tick[0] < 60) {
                return;
            }
            String failure = null;
            for (int i = 0; i < ids.length && failure == null; i++) {
                GenericFlyingBeastEntity beast = large[i];
                String where = " (" + beast.position() + ", flying " + beast.isFlying() + ")";
                if (!beast.walksTheGround()) {
                    failure = ids[i] + " is not a winged walker";
                } else if (beast.getNavigation() instanceof FlyingPathNavigation) {
                    failure = ids[i] + " still navigates as a flier";
                } else if (!stood[i]) {
                    failure = ids[i] + " never stood on the ground in three seconds" + where;
                } else if (floated[i]) {
                    failure = ids[i] + " hung weightless without flying" + where;
                }
            }
            if (failure == null && (doxy.walksTheGround() || !(doxy.getNavigation() instanceof FlyingPathNavigation))) {
                failure = "a doxy stopped being a small flier";
            }
            scene.finish(failure);
        });
    }

    private static void startledFlierTakesOffAndLands(GameTestHelper helper) {
        WizardTestSupport.forceChunks(helper, new BlockPos(-48, 1, -48), new BlockPos(56, 1, 56));
        Scene scene = new Scene(helper).floor(0, 0, 8, 8, 1);
        GenericFlyingBeastEntity granian = scene.flier("granian", new BlockPos(4, 2, 4));
        Cow cow = scene.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        cow.setNoAi(true);
        int[] tick = {0};
        double[] ground = {0};
        double[] highest = {0};
        boolean[] flew = {false};
        int[] struckAt = {-1};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            tick[0]++;
            if (struckAt[0] < 0) {
                // The blow counts once the server can resolve who dealt it: the attacker is looked up by id, and a
                // cow whose chunk is still coming up is not found yet. Start the clock when it is.
                if (tick[0] >= 30 && granian.onGround() && !granian.isFlying()) {
                    ground[0] = granian.getY();
                    // A granian cannot fight (no attack), so a blow leaves it nothing to do but go.
                    granian.setLastHurtByMob(cow);
                    if (granian.getLastHurtByMob() == cow) {
                        struckAt[0] = tick[0];
                    }
                }
                if (struckAt[0] < 0) {
                    if (tick[0] > 400) {
                        done[0] = true;
                        scene.finish("the granian never stood still to be struck (onGround " + granian.onGround() + ")");
                    }
                    return;
                }
            }
            highest[0] = Math.max(highest[0], granian.getY() - ground[0]);
            flew[0] |= granian.isFlying();
            String failure = null;
            if (!flew[0] && tick[0] > struckAt[0] + 90) {
                failure = "a struck granian had not taken off 90 ticks later (goals " + running(granian)
                        + ", onGround " + granian.onGround() + ", noAi " + granian.isNoAi() + ", hurtBy "
                        + granian.getLastHurtByMob() + " at " + granian.getLastHurtByMobTimestamp() + "/"
                        + granian.tickCount + ", target " + granian.getTarget() + ", creatures module "
                        + at.koopro.wizardsandbeasts.module.ModuleManager.isEnabled(
                                at.koopro.wizardsandbeasts.module.Module.CREATURES) + ", at " + granian.position() + ")";
            } else if (flew[0] && !granian.isFlying() && granian.onGround()) {
                if (highest[0] <= 4.0) {
                    failure = "its flight never rose more than " + highest[0] + " blocks";
                } else if (granian.isNoGravity()) {
                    failure = "it landed still weightless";
                }
            } else if (tick[0] > 1900) {
                failure = "the granian never came down (flying " + granian.isFlying() + ", at " + granian.position() + ")";
            } else {
                return;
            }
            done[0] = true;
            scene.finish(failure);
        });
    }

    private static void hunterReachesATargetOnAPillar(GameTestHelper helper) {
        WizardTestSupport.forceChunks(helper, new BlockPos(-32, 1, -32), new BlockPos(48, 1, 40));
        Scene scene = new Scene(helper).floor(0, 0, 22, 8, 1);
        for (int y = 2; y <= 8; y++) {
            scene.stone(new BlockPos(14, y, 4));
        }
        GenericFlyingBeastEntity griffin = scene.flier("griffin", new BlockPos(3, 2, 4));
        Cow cow = scene.spawn(EntityType.COW, new BlockPos(14, 9, 4));
        cow.setNoAi(true);
        float full = cow.getHealth();
        int[] tick = {0};
        boolean[] flew = {false};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            tick[0]++;
            if (tick[0] >= 20 && griffin.getTarget() != cow && cow.isAlive()) {
                griffin.setTarget(cow);
            }
            flew[0] |= griffin.isFlying();
            String failure;
            if (cow.getHealth() < full || !cow.isAlive()) {
                failure = flew[0] ? null : "the cow on the pillar was hurt, but the griffin never flew (griffin "
                        + griffin.position() + ", cow " + cow.position() + ", last hurt by "
                        + cow.getLastDamageSource() + ", goals " + running(griffin) + ")";
            } else if (tick[0] > 1100) {
                failure = "a griffin never struck a target seven blocks up on a pillar (griffin " + griffin.position()
                        + " flying " + griffin.isFlying() + ", cow " + cow.position() + ", goals " + running(griffin) + ")";
            } else {
                return;
            }
            done[0] = true;
            scene.finish(failure);
        });
    }

    private static void thunderbirdStormsOnlyOnTheWing(GameTestHelper helper) {
        WizardTestSupport.forceChunks(helper, new BlockPos(-32, 1, -32), new BlockPos(48, 1, 40));
        Scene scene = new Scene(helper).floor(0, 0, 16, 8, 1);
        GenericFlyingBeastEntity thunderbird = scene.flier("thunderbird", new BlockPos(3, 2, 4));
        Cow cow = scene.spawn(EntityType.COW, new BlockPos(8, 2, 4));
        cow.setNoAi(true);
        int[] tick = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            tick[0]++;
            if (tick[0] >= 20 && thunderbird.getTarget() != cow && cow.isAlive()) {
                thunderbird.setTarget(cow);   // five blocks off and level: any other flier would walk up and bite
            }
            String failure;
            if (thunderbird.getCooldown("thunderbird") > 0) {
                failure = thunderbird.onGround() ? "the thunderbird called its storm standing on the ground" : null;
            } else if (tick[0] > 400) {
                failure = "a thunderbird with a target never raised its storm (flying " + thunderbird.isFlying()
                        + ", at " + thunderbird.position() + ", goals " + running(thunderbird) + ")";
            } else {
                return;
            }
            done[0] = true;
            // The storm is world weather: put the sky back for whatever runs next.
            helper.getLevel().setWeatherParameters(6000, 0, false, false);
            scene.finish(failure);
        });
    }

    private static String running(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                .map(g -> g.getGoal().getClass().getSimpleName()).toList().toString();
    }

    private static EntityType<?> type(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
    }
}
