package at.koopro.wizardsandbeasts.world.tree;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import at.koopro.wizardsandbeasts.registry.ModBlocks;

public class YewTreeFeature extends Feature<NoneFeatureConfiguration> {

    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public YewTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        BlockState log = ModBlocks.YEW.log().get().defaultBlockState();
        BlockState leaves = ModBlocks.YEW.leaves().get().defaultBlockState();

        int height = 6 + random.nextInt(4); // 6-9

        // 2x2 base trunk for first few blocks
        int thickHeight = Math.max(2, height / 2);
        for (int y = 0; y < height; y++) {
            if (y < thickHeight) {
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        BlockPos pos = origin.offset(dx, y, dz);
                        if (canReplace(level, pos)) setBlock(level, pos, log);
                    }
                }
            } else {
                BlockPos pos = origin.offset(0, y, 0);
                if (canReplace(level, pos)) setBlock(level, pos, log);
            }
        }

        // Low branches, short and slightly downward
        int branchStartY = 3 + random.nextInt(2); // 3-4
        int branchCount = 4 + random.nextInt(3);
        for (int i = 0; i < branchCount; i++) {
            int y = branchStartY + random.nextInt(Math.max(1, height - branchStartY));
            Direction dir = HORIZONTAL_DIRECTIONS[random.nextInt(HORIZONTAL_DIRECTIONS.length)];
            int length = 2 + random.nextInt(2); // 2-3
            generateYewBranch(level, origin.above(y), dir, length, log, leaves, random);
        }

        // Dense leaf cover around upper trunk
        generateDenseLeaves(level, origin.above(branchStartY), height - branchStartY + 2, leaves);

        return true;
    }

    private boolean canReplace(WorldGenLevel level, BlockPos pos) {
        return level.isEmptyBlock(pos);
    }

    private void generateYewBranch(WorldGenLevel level, BlockPos base, Direction direction, int length,
                                   BlockState log, BlockState leaves, RandomSource random) {
        BlockPos.MutableBlockPos cursor = base.mutable();
        for (int i = 0; i < length; i++) {
            cursor.move(direction);
            if (i > 0 && random.nextFloat() < 0.4f) {
                cursor.move(Direction.DOWN);
            }
            if (canReplace(level, cursor)) {
                setBlock(level, cursor, log);
            }
            // Surround with dense leaves
            generateSmallLeafCluster(level, cursor, 1 + random.nextInt(2), leaves);
        }
    }

    private void generateDenseLeaves(WorldGenLevel level, BlockPos start, int verticalSpan, BlockState leaves) {
        for (int dy = -1; dy <= verticalSpan; dy++) {
            int radius = 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = start.offset(dx, dy, dz);
                    if (level.isEmptyBlock(pos)) {
                        setBlock(level, pos, leaves);
                    }
                }
            }
        }
    }

    private void generateSmallLeafCluster(WorldGenLevel level, BlockPos center, int radius, BlockState leaves) {
        int rSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= rSq) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (level.isEmptyBlock(pos)) {
                            setBlock(level, pos, leaves);
                        }
                    }
                }
            }
        }
    }
}

