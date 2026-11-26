package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModVillager;
import at.koopro.wizardsandbeasts.wand.bench.BenchPart;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlock;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The wandmaker's bench is two blocks wide (owner, 2026-11-26). A bench without properties (every bench saved before,
 * every one a structure template places) is solo and grows its side half when there is room. A boxed-in one stays solo
 * and still works, and grows once a side opens. Breaking either half takes both and drops one bench. Villagers see one
 * workstation, not two.
 *
 * <p>Built well above the shared ground, because scenarios here share ground horizontally (see the GameTest isolation
 * notes) and a bench that grows sideways must not grow into a neighbour.
 */
public final class BenchTwoWideTests {

    private static final int Y = 14;

    private BenchTwoWideTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("bench_solo_grows_into_a_pair",
                "bench: a bench placed without properties (an old save, a template) grows its side half beside it, "
                        + "facing the same way, and a click on the side half finds the working part",
                BenchTwoWideTests::soloGrows);
        tests.add("bench_boxed_in_stays_solo_until_a_side_opens",
                "bench: with no room on any side the bench stays solo and keeps its block entity; when one side "
                        + "opens it grows there, turned to face so",
                BenchTwoWideTests::boxedIn);
        tests.add("bench_breaking_either_half_takes_both",
                "bench: breaking the side half removes the working part and drops one bench; breaking the working "
                        + "part removes the side half and drops one bench",
                BenchTwoWideTests::breakingTakesBoth);
        tests.add("bench_is_one_workstation",
                "bench: the Wandmaker's workstation is the working part only (main or solo), never the side half",
                BenchTwoWideTests::oneWorkstation);
    }

    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, Y, z));
    }

    private static void soloGrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos main = at(helper, 2, 2);
        level.setBlockAndUpdate(main, ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState());
        check(helper, level.getBlockState(main).getValue(WandmakersBenchBlock.PART) == BenchPart.SOLO,
                () -> "a bench without properties is not solo");
        helper.succeedWhen(() -> {
            BlockState mainState = level.getBlockState(main);
            check(helper, mainState.getValue(WandmakersBenchBlock.PART) == BenchPart.MAIN, () -> "still " + mainState);
            Direction facing = mainState.getValue(WandmakersBenchBlock.FACING);
            check(helper, facing == Direction.NORTH, () -> "an unobstructed bench turned to " + facing);
            BlockPos side = WandmakersBenchBlock.sideOf(main, facing);
            BlockState sideState = level.getBlockState(side);
            check(helper, sideState.is(ModBlocks.WANDMAKERS_BENCH.get())
                    && sideState.getValue(WandmakersBenchBlock.PART) == BenchPart.SIDE
                    && sideState.getValue(WandmakersBenchBlock.FACING) == facing, () -> "no side half: " + sideState);
            check(helper, WandmakersBenchBlock.mainOf(sideState, side).equals(main), () -> "the side half lost its main");
            check(helper, level.getBlockEntity(side) == null, () -> "the side half has a block entity");
            check(helper, level.getBlockEntity(main) instanceof WandmakersBenchBlockEntity, () -> "the main half lost its block entity");
        });
    }

    private static void boxedIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos main = at(helper, 6, 6);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(main.relative(d), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(main, ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            check(helper, level.getBlockState(main).getValue(WandmakersBenchBlock.PART) == BenchPart.SOLO,
                    () -> "a boxed-in bench grew into stone");
            check(helper, level.getBlockEntity(main) instanceof WandmakersBenchBlockEntity, () -> "a solo bench has no block entity");
            // Open the east side. Facing north the side half stands west, so the bench must turn to grow east.
            level.setBlockAndUpdate(main.east(), Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(140, () -> {
            BlockState state = level.getBlockState(main);
            check(helper, state.getValue(WandmakersBenchBlock.PART) == BenchPart.MAIN, () -> "did not grow once a side opened: " + state);
            BlockPos side = WandmakersBenchBlock.sideOf(main, state.getValue(WandmakersBenchBlock.FACING));
            check(helper, side.equals(main.east()), () -> "grew to " + side + ", not into the open side " + main.east());
            helper.succeed();
        });
    }

    private static void breakingTakesBoth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = at(helper, 2, 8);
        BlockPos second = at(helper, 6, 12);
        level.setBlockAndUpdate(first, ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState());
        level.setBlockAndUpdate(second, ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState());
        helper.runAfterDelay(5, () -> {
            BlockPos firstSide = WandmakersBenchBlock.sideOf(first, level.getBlockState(first).getValue(WandmakersBenchBlock.FACING));
            level.destroyBlock(firstSide, true);
            check(helper, level.getBlockState(first).isAir(), () -> "breaking the side half left " + level.getBlockState(first));
            level.destroyBlock(second, true);
            BlockPos secondSide = WandmakersBenchBlock.sideOf(second, Direction.NORTH);
            check(helper, level.getBlockState(secondSide).isAir(), () -> "breaking the main half left " + level.getBlockState(secondSide));
        });
        helper.runAfterDelay(10, () -> {
            check(helper, benches(level, first) == 1, () -> "breaking a pair by its side dropped " + benches(level, first) + " benches");
            check(helper, benches(level, second) == 1, () -> "breaking a pair by its main dropped " + benches(level, second) + " benches");
            helper.succeed();
        });
    }

    private static int benches(ServerLevel level, BlockPos around) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(around).inflate(2.5)).stream()
                .filter(e -> e.getItem().is(ModBlocks.WANDMAKERS_BENCH_ITEM.get()))
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void oneWorkstation(GameTestHelper helper) {
        var poi = ModVillager.OLLIVANDERS_BENCH.get();
        for (BlockState state : ModBlocks.WANDMAKERS_BENCH.get().getStateDefinition().getPossibleStates()) {
            boolean working = state.getValue(WandmakersBenchBlock.PART) != BenchPart.SIDE;
            check(helper, poi.is(state) == working, () -> "workstation " + (working ? "misses " : "claims ") + state);
        }
        helper.succeed();
    }
}
