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

public class ElderTreeFeature extends Feature<NoneFeatureConfiguration> {

    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public ElderTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        BlockState log = ModBlocks.ELDER.log().get().defaultBlockState();
        BlockState leaves = ModBlocks.ELDER.leaves().get().defaultBlockState();

        int height = 12 + random.nextInt(5); // 12-16

        // Trunk
        for (int y = 0; y < height; y++) {
            BlockPos pos = origin.above(y);
            if (!canReplace(level, pos)) continue;
            setBlock(level, pos, log);
        }

        // Branches starting above height 6
        int branchYStart = 6;
        int branchCount = 3 + random.nextInt(3); // 3-5
        for (int i = 0; i < branchCount; i++) {
            int branchBaseY = branchYStart + random.nextInt(Math.max(1, height - branchYStart - 2));
            Direction dir = HORIZONTAL_DIRECTIONS[random.nextInt(HORIZONTAL_DIRECTIONS.length)];
            int length = 4 + random.nextInt(3); // 4-6

            generateCurvedBranch(level, origin.above(branchBaseY), dir, length, log, leaves, random, 4 + random.nextInt(2));
        }

        // Crown leaf cluster at the top
        BlockPos top = origin.above(height);
        generateLeafBlob(level, top, 4 + random.nextInt(2), leaves);

        return true;
    }

    private boolean canReplace(WorldGenLevel level, BlockPos pos) {
        return level.isEmptyBlock(pos);
    }

    private void generateCurvedBranch(WorldGenLevel level, BlockPos base, Direction direction, int length,
                                      BlockState log, BlockState leaves, RandomSource random, int leafRadius) {
        BlockPos.MutableBlockPos cursor = base.mutable();

        for (int i = 0; i < length; i++) {
            cursor.move(direction);

            // slight upward curve towards the end
            if (i > 1 && random.nextFloat() < 0.5f) {
                cursor.move(Direction.UP);
            }

            if (canReplace(level, cursor)) {
                setBlock(level, cursor, log);
            }
        }

        // Leaf blob at the branch tip
        generateLeafBlob(level, cursor, leafRadius, leaves);
    }

    private void generateLeafBlob(WorldGenLevel level, BlockPos center, int radius, BlockState leaves) {
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

