package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.block.furniture.ArchBlock;
import at.koopro.wizardsandbeasts.block.furniture.ColumnBlock;
import at.koopro.wizardsandbeasts.block.furniture.ConnectingFurnitureBlock;
import at.koopro.wizardsandbeasts.block.furniture.FurnitureBlock;
import at.koopro.wizardsandbeasts.block.furniture.SeatEntity;
import at.koopro.wizardsandbeasts.registry.FurnitureBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * World-decoration pieces through the real placement path: every one places from each cardinal and faces the way it
 * should (back to the placer, or out from the wall it was hung on), has an outline, and drops itself; runs and stacks
 * pick the right part; a chair seats a player and lets go when it is broken.
 *
 * <p>Scenes are built at {@link #HEIGHT}, a band no other scenario uses (static blocks only), and every block a scene
 * sets is set back to air on the way out, pass or fail.
 */
public final class FurnitureTests {

    private static final int HEIGHT = 200;
    private static final List<Direction> CARDINALS =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private FurnitureTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("furniture_every_piece_places_faces_and_drops",
                "furniture: every piece places from each cardinal, faces right, has an outline and drops itself",
                FurnitureTests::everyPiecePlaces);
        tests.add("furniture_runs_and_stacks_pick_their_parts",
                "furniture: tables, arches and columns choose their part from their neighbours",
                FurnitureTests::runsAndStacks);
        tests.add("furniture_chair_seats_a_player_and_lets_go",
                "furniture: using a chair seats the player; breaking it stands them up and the seat goes",
                FurnitureTests::chairSeats);
    }

    /** Blocks a scene set, put back to air on the way out. */
    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<BlockPos> set = new ArrayList<>();

        Scene(GameTestHelper helper) {
            this.helper = helper;
        }

        BlockPos at(int x, int y, int z) {
            return new BlockPos(x, HEIGHT + y, z);
        }

        void set(BlockPos pos, Block block) {
            helper.setBlock(pos, block);
            set.add(pos);
        }

        BlockState place(ServerPlayer player, BlockItem item, BlockPos against, Direction face, Direction look) {
            BlockPos abs = helper.absolutePos(against);
            player.snapTo(abs.getX() + 0.5 - look.getStepX() * 3, abs.getY() + 1, abs.getZ() + 0.5 - look.getStepZ() * 3,
                    look.toYRot(), 0f);
            player.setYHeadRot(look.toYRot());
            ItemStack stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            Vec3 hit = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            InteractionResult result = item.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    stack, new BlockHitResult(hit, face, abs, false)));
            BlockPos placed = against.relative(face);
            set.add(placed);
            BlockState state = helper.getBlockState(placed);
            WizardTestSupport.check(helper, result.consumesAction(),
                    () -> item + " looking " + look + " onto " + face + " was refused (" + result + "); found " + state);
            return state;
        }

        @Override
        public void close() {
            for (int i = set.size() - 1; i >= 0; i--) {
                helper.setBlock(set.get(i), Blocks.AIR);
            }
            set.clear();
        }
    }

    private static boolean wallMounted(Block block) {
        return block instanceof LadderBlock || (block instanceof FurnitureBlock f && f.spec().wall());
    }

    private static void everyPiecePlaces(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Decorator");
        try (Scene scene = new Scene(helper)) {
            BlockPos floor = scene.at(1, 0, 1);
            BlockPos wall = scene.at(1, 1, 1);
            for (var item : FurnitureBlocks.ITEMS) {
                BlockItem blockItem = (BlockItem) item.get();
                Block block = blockItem.getBlock();
                for (Direction look : CARDINALS) {
                    scene.set(floor, Blocks.STONE);
                    boolean onWall = wallMounted(block);
                    Direction face = onWall ? look.getOpposite() : Direction.UP;
                    if (onWall) {
                        scene.set(wall, Blocks.STONE);
                    }
                    BlockState state = scene.place(player, blockItem, onWall ? wall : floor, face, look);
                    WizardTestSupport.check(helper, state.is(block),
                            () -> block + " did not place looking " + look + "; found " + state);
                    if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                        Direction expected = onWall ? face : look.getOpposite();
                        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
                        WizardTestSupport.check(helper, facing == expected,
                                () -> block + " placed looking " + look + " faces " + facing + ", expected " + expected);
                    }
                    BlockPos placed = helper.absolutePos((onWall ? wall : floor).relative(face));
                    WizardTestSupport.check(helper, !state.getShape(helper.getLevel(), placed).isEmpty(),
                            () -> block + " has no outline facing " + look + ", so it cannot be picked or broken");
                    List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), placed, null);
                    WizardTestSupport.check(helper, drops.size() == 1 && drops.getFirst().is(blockItem),
                            () -> block + " drops " + drops + ", not itself");
                    scene.close();
                }
            }
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void runsAndStacks(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Builder");
        try (Scene scene = new Scene(helper)) {
            // A table run, facing north (placer looks south): its START is the west end (the authoring frame's low x).
            for (int x = 0; x < 3; x++) {
                scene.set(scene.at(x, 0, 1), Blocks.STONE);
            }
            BlockItem table = (BlockItem) FurnitureBlocks.REFECTORY_TABLE.asItem();
            for (int x = 0; x < 3; x++) {
                scene.place(player, table, scene.at(x, 0, 1), Direction.UP, Direction.SOUTH);
            }
            expectRun(helper, scene.at(0, 1, 1), ConnectingFurnitureBlock.Run.START);
            expectRun(helper, scene.at(1, 1, 1), ConnectingFurnitureBlock.Run.MIDDLE);
            expectRun(helper, scene.at(2, 1, 1), ConnectingFurnitureBlock.Run.END);
            helper.setBlock(scene.at(1, 1, 1), Blocks.AIR);
            expectRun(helper, scene.at(0, 1, 1), ConnectingFurnitureBlock.Run.SINGLE);
            expectRun(helper, scene.at(2, 1, 1), ConnectingFurnitureBlock.Run.SINGLE);
            scene.close();

            // Arches over openings of one, two and three: a lancet, a pair meeting in a point, a three-block arch.
            for (int x = 0; x < 3; x++) {
                scene.set(scene.at(x, 0, 1), Blocks.STONE);
            }
            BlockItem arch = (BlockItem) FurnitureBlocks.HOGWARTS_GOTHIC_ARCH.asItem();
            scene.place(player, arch, scene.at(0, 0, 1), Direction.UP, Direction.SOUTH);
            expectArch(helper, scene.at(0, 1, 1), ArchBlock.Part.SINGLE);
            scene.place(player, arch, scene.at(1, 0, 1), Direction.UP, Direction.SOUTH);
            expectArch(helper, scene.at(0, 1, 1), ArchBlock.Part.PAIR_START);
            expectArch(helper, scene.at(1, 1, 1), ArchBlock.Part.PAIR_END);
            scene.place(player, arch, scene.at(2, 0, 1), Direction.UP, Direction.SOUTH);
            expectArch(helper, scene.at(0, 1, 1), ArchBlock.Part.START);
            expectArch(helper, scene.at(1, 1, 1), ArchBlock.Part.MIDDLE);
            expectArch(helper, scene.at(2, 1, 1), ArchBlock.Part.END);
            scene.close();

            // Over a window the arch's curve takes the pane's glass; with the pane gone it is open again.
            scene.set(scene.at(0, 0, 1), Blocks.STONE);
            scene.place(player, (BlockItem) FurnitureBlocks.CRIMSON_LEADED_GLASS_PANE.asItem(), scene.at(0, 0, 1),
                    Direction.UP, Direction.SOUTH);
            scene.place(player, arch, scene.at(0, 1, 1), Direction.UP, Direction.SOUTH);
            expectGlass(helper, scene.at(0, 2, 1), ArchBlock.Glass.CRIMSON);
            helper.setBlock(scene.at(0, 1, 1), Blocks.AIR);
            expectGlass(helper, scene.at(0, 2, 1), ArchBlock.Glass.NONE);
            scene.close();

            // A column three high: base, shaft, capital; one alone is a pedestal.
            scene.set(scene.at(0, 0, 1), Blocks.STONE);
            BlockItem column = (BlockItem) FurnitureBlocks.HOGWARTS_STONE_COLUMN.asItem();
            scene.place(player, column, scene.at(0, 0, 1), Direction.UP, Direction.SOUTH);
            expectColumn(helper, scene.at(0, 1, 1), ColumnBlock.Part.SINGLE);
            scene.place(player, column, scene.at(0, 1, 1), Direction.UP, Direction.SOUTH);
            scene.place(player, column, scene.at(0, 2, 1), Direction.UP, Direction.SOUTH);
            expectColumn(helper, scene.at(0, 1, 1), ColumnBlock.Part.BASE);
            expectColumn(helper, scene.at(0, 2, 1), ColumnBlock.Part.SHAFT);
            expectColumn(helper, scene.at(0, 3, 1), ColumnBlock.Part.CAPITAL);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void expectRun(GameTestHelper helper, BlockPos pos, ConnectingFurnitureBlock.Run run) {
        BlockState state = helper.getBlockState(pos);
        WizardTestSupport.check(helper, state.hasProperty(ConnectingFurnitureBlock.RUN)
                && state.getValue(ConnectingFurnitureBlock.RUN) == run, () -> pos + " is " + state + ", expected " + run);
    }

    private static void expectArch(GameTestHelper helper, BlockPos pos, ArchBlock.Part part) {
        BlockState state = helper.getBlockState(pos);
        WizardTestSupport.check(helper, state.hasProperty(ArchBlock.PART) && state.getValue(ArchBlock.PART) == part,
                () -> pos + " is " + state + ", expected " + part);
    }

    private static void expectGlass(GameTestHelper helper, BlockPos pos, ArchBlock.Glass glass) {
        BlockState state = helper.getBlockState(pos);
        WizardTestSupport.check(helper, state.hasProperty(ArchBlock.GLASS) && state.getValue(ArchBlock.GLASS) == glass,
                () -> pos + " is " + state + ", expected glass " + glass);
    }

    private static void expectColumn(GameTestHelper helper, BlockPos pos, ColumnBlock.Part part) {
        BlockState state = helper.getBlockState(pos);
        WizardTestSupport.check(helper, state.hasProperty(ColumnBlock.PART) && state.getValue(ColumnBlock.PART) == part,
                () -> pos + " is " + state + ", expected " + part);
    }

    private static void chairSeats(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Sitter");
        Scene scene = new Scene(helper);
        BlockPos floor = scene.at(1, 0, 1);
        scene.set(floor, Blocks.STONE);
        BlockState chair = scene.place(player, (BlockItem) FurnitureBlocks.ACADEMY_CHAIR.asItem(), floor, Direction.UP,
                Direction.SOUTH);
        BlockPos seat = helper.absolutePos(floor.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult used = chair.useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(seat), Direction.UP, seat, false));
        WizardTestSupport.check(helper, used.consumesAction() && player.getVehicle() instanceof SeatEntity,
                () -> "using the chair returned " + used + " and the player rides " + player.getVehicle());
        double hips = player.getVehicle().getY() - seat.getY();
        WizardTestSupport.check(helper, Math.abs(hips - 0.69) < 0.01,
                () -> "the seat is " + hips + " above the floor, expected the chair's seat height 0.69");
        helper.setBlock(floor.above(), Blocks.AIR);
        helper.runAfterDelay(3, () -> {
            try {
                WizardTestSupport.check(helper, !player.isPassenger(),
                        () -> "the chair is gone but the player still rides " + player.getVehicle());
                WizardTestSupport.check(helper, helper.getLevel().getEntitiesOfClass(SeatEntity.class,
                                new net.minecraft.world.phys.AABB(seat).inflate(2)).isEmpty(),
                        () -> "a seat outlived its chair");
                helper.succeed();
            } finally {
                scene.close();
                WizardTestSupport.retire(helper, player);
            }
        });
    }
}
