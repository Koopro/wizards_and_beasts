package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.diagon.DiagonArchwayBlock;
import at.koopro.wizardsandbeasts.diagon.DiagonArchwayMovingBlock;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The wall behind the Leaky Cauldron, as the yard builds it: five bricks wide and six high under a rounded crown, the
 * key three up in the middle. A wand on the key opens every brick of the arch; with nobody standing in it, the arch
 * closes again and every brick is resting wall once more. Built high, clear of the arenas below (isolation by height).
 */
public final class DiagonWallTests {

    private static final int HEIGHT = 250;
    /** Opening, standing open, and the closing animation, with room to spare. */
    private static final int CLOSED_BY = 160 + 32 + 60;

    private DiagonWallTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("diagon_wall_opens_to_a_wand_and_closes_behind",
                "diagon: a wand on the key opens the whole arch, which closes again when nobody stands in it",
                DiagonWallTests::opensAndCloses, CLOSED_BY + 100);
    }

    /** The arch's bricks, test-relative: the wall runs along z at x 2. */
    private static List<BlockPos> arch() {
        List<BlockPos> bricks = new ArrayList<>();
        for (int z = 0; z < 5; z++) {
            for (int y = 0; y < 6; y++) {
                if ((y == 4 && (z == 0 || z == 4)) || (y == 5 && z != 2)) {
                    continue;
                }
                bricks.add(new BlockPos(2, HEIGHT + y, z));
            }
        }
        return bricks;
    }

    private static void opensAndCloses(GameTestHelper helper) {
        BlockPos key = new BlockPos(2, HEIGHT + 2, 2);
        WizardTestSupport.forceChunks(helper, new BlockPos(0, HEIGHT, 0), new BlockPos(4, HEIGHT + 6, 4));
        for (BlockPos brick : arch()) {
            helper.setBlock(brick, ModBlocks.DIAGON_ARCHWAY.get().defaultBlockState()
                    .setValue(DiagonArchwayBlock.KEY, brick.equals(key)));
        }
        ServerPlayer wizard = WizardTestSupport.placeMockPlayer(helper, "DiagonTapper", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, wizard);
        ItemStack wand = BuiltInRegistries.ITEM.stream().filter(item -> item instanceof WandItem).findFirst()
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        check(helper, !wand.isEmpty(), () -> "no wand item is registered");
        wizard.setItemInHand(InteractionHand.MAIN_HAND, wand);
        helper.startSequence()
                .thenExecute(() -> {
                    BlockPos at = helper.absolutePos(key);
                    BlockState state = helper.getLevel().getBlockState(at);
                    state.useItemOn(wand, helper.getLevel(), wizard, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(at), Direction.WEST, at, false));
                    for (BlockPos brick : arch()) {
                        BlockState now = helper.getBlockState(brick);
                        check(helper, now.is(ModBlocks.DIAGON_ARCHWAY_MOVING.get())
                                        && now.getValue(DiagonArchwayMovingBlock.OPEN),
                                () -> "the brick at " + brick + " did not open: " + now);
                    }
                })
                .thenExecuteAfter(CLOSED_BY, () -> {
                    for (BlockPos brick : arch()) {
                        BlockState now = helper.getBlockState(brick);
                        check(helper, now.is(ModBlocks.DIAGON_ARCHWAY.get()),
                                () -> "the brick at " + brick + " is not resting wall again: " + now);
                    }
                    check(helper, helper.getBlockState(key).getValue(DiagonArchwayBlock.KEY),
                            () -> "the key brick lost its key after closing");
                })
                .thenExecute(() -> WizardTestSupport.retire(helper, wizard))
                .thenSucceed();
    }
}
