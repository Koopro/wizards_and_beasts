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

public class RowanTreeFeature extends Feature<NoneFeatureConfiguration> {

    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public RowanTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        BlockState log = ModBlocks.ROWAN.log().get().defaultBlockState();
        BlockState leaves = ModBlocks.ROWAN.leaves().get().defaultBlockState();

        int height = 7 + random.nextInt(4); // 7-10

        // Slim straight trunk
        for (int y = 0; y < height; y++) {
            BlockPos pos = origin.above(y);
            if (canReplace(level, pos)) {
                setBlock(level, pos, log);
            }
        }

        // Thin, upward-angled branches
        int branchBaseHeight = 5 + random.nextInt(2); // 5-6
        int branchCount = 2 + random.nextInt(3); // 2-4

        for (int i = 0; i < branchCount; i++) {
            Direction dir = HORIZONTAL_DIRECTIONS[random.nextInt(HORIZONTAL_DIRECTIONS.length)];
            int length = 3 + random.nextInt(2); // 3-4
            generateUpwardBranch(level, origin.above(branchBaseHeight + random.nextInt(Math.max(1, height - branchBaseHeight - 1))),
                    dir, length, log, leaves, random);
        }

        // Light crown
        BlockPos top = origin.above(height);
        generateSparseLeaves(level, top, 2, leaves);

        return true;
    }

    private boolean canReplace(WorldGenLevel level, BlockPos pos) {
        return level.isEmptyBlock(pos);
    }

    private void generateUpwardBranch(WorldGenLevel level, BlockPos base, Direction direction, int length,
                                      BlockState log, BlockState leaves, RandomSource random) {
        BlockPos.MutableBlockPos cursor = base.mutable();

        for (int i = 0; i < length; i++) {
            cursor.move(direction);
            if (random.nextFloat() < 0.8f) {
                cursor.move(Direction.UP);
            }
            if (canReplace(level, cursor)) {
                setBlock(level, cursor, log);
            }

            // Small, airy leaf tufts along the branch
            if (random.nextFloat() < 0.5f) {
                generateSparseLeaves(level, cursor, 2, leaves);
            }
        }
    }

    private void generateSparseLeaves(WorldGenLevel level, BlockPos center, int radius, BlockState leaves) {
        int rSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= rSq && (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) >= 1)) {
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

