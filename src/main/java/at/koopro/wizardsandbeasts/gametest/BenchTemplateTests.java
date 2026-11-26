package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.wand.bench.BenchPart;
import at.koopro.wizardsandbeasts.wand.bench.BenchRole;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlock;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The benches the world already has (P7 of the 2026-11-26 remake): the two templates that carry one were saved when
 * the bench was one block. They load as a solo bench, which grows its second half where there is room, and the bench
 * still opens. A template must never hold a side half on its own: it has no block entity and would be furniture.
 */
public final class BenchTemplateTests {

    private static final int Y = 30;

    private BenchTemplateTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("bench_templates_hold_only_working_benches",
                "bench templates: Diagon Alley's and the hamlet wandmaker's benches are solo or main, never a side "
                        + "half on its own",
                BenchTemplateTests::templatesHoldWorkingBenches);
        tests.add("bench_hamlet_wandmaker_bench_grows_and_opens",
                "bench templates: the hamlet wandmaker's bench, placed from its template, keeps its block entity, "
                        + "grows into a pair where there is room and opens the making screen",
                BenchTemplateTests::hamletBenchGrowsAndOpens);
    }

    private static StructureTemplate template(GameTestHelper helper, String path) {
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
        StructureTemplate t = helper.getLevel().getStructureManager().get(id).orElse(null);
        check(helper, t != null, () -> "template " + id + " is missing");
        return t;
    }

    private static List<BlockState> benchStates(StructureTemplate t) {
        List<BlockState> out = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                ModBlocks.WANDMAKERS_BENCH.get())) {
            out.add(info.state());
        }
        return out;
    }

    private static void templatesHoldWorkingBenches(GameTestHelper helper) {
        for (String path : List.of("diagon_alley/alley/a", "wizarding_hamlet/wandmaker/a")) {
            List<BlockState> benches = benchStates(template(helper, path));
            check(helper, !benches.isEmpty(), () -> path + " has no bench");
            for (BlockState state : benches) {
                check(helper, state.getValue(WandmakersBenchBlock.PART).isWorking(),
                        () -> path + " holds a side half on its own: " + state);
            }
        }
        helper.succeed();
    }

    private static void hamletBenchGrowsAndOpens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate t = template(helper, "wizarding_hamlet/wandmaker/a");
        BlockPos origin = helper.absolutePos(new BlockPos(0, Y, 0));
        t.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        List<BlockPos> benches = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(origin, new StructurePlaceSettings(),
                ModBlocks.WANDMAKERS_BENCH.get())) {
            benches.add(info.pos());
        }
        check(helper, !benches.isEmpty(), () -> "the hamlet wandmaker's template placed no bench");
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "BenchVisitor", GameType.SURVIVAL);
        helper.runAfterDelay(5, () -> {
            try {
                for (BlockPos pos : benches) {
                    BlockState state = level.getBlockState(pos);
                    check(helper, state.is(ModBlocks.WANDMAKERS_BENCH.get()), () -> "the bench at " + pos + " is gone: " + state);
                    BenchPart part = state.getValue(WandmakersBenchBlock.PART);
                    check(helper, part == BenchPart.MAIN || part == BenchPart.SOLO, () -> "the bench at " + pos + " is " + part);
                    if (part == BenchPart.MAIN) {
                        BlockPos side = WandmakersBenchBlock.sideOf(pos, state.getValue(WandmakersBenchBlock.FACING));
                        check(helper, level.getBlockState(side).getValue(WandmakersBenchBlock.PART) == BenchPart.SIDE,
                                () -> "the bench at " + pos + " grew without a side half");
                    }
                    check(helper, level.getBlockEntity(pos) instanceof WandmakersBenchBlockEntity,
                            () -> "the bench at " + pos + " has no block entity");
                    BenchRole.MAKE.open(player, (WandmakersBenchBlockEntity) level.getBlockEntity(pos));
                    check(helper, player.containerMenu instanceof WandmakersBenchMenu,
                            () -> "the bench at " + pos + " did not open: " + player.containerMenu);
                    player.closeContainer();
                }
                helper.succeed();
            } finally {
                WizardTestSupport.retire(helper, player);
            }
        });
    }
}
