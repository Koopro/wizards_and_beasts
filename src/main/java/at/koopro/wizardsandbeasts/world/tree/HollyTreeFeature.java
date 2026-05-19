package at.koopro.wizardsandbeasts.world.tree;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import at.koopro.wizardsandbeasts.registry.ModBlocks;

public class HollyTreeFeature extends Feature<NoneFeatureConfiguration> {

    public HollyTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        BlockState log = ModBlocks.HOLLY.log().get().defaultBlockState();
        BlockState leaves = ModBlocks.HOLLY.leaves().get().defaultBlockState();

        int height = 4 + random.nextInt(3); // 4-6

        // Simple straight trunk
        for (int y = 0; y < height; y++) {
            BlockPos pos = origin.above(y);
            if (canReplace(level, pos)) {
                setBlock(level, pos, log);
            }
        }

        // Small offshoots near the top (1-2 blocks)
        int topY = height - 1;
        for (int i = 0; i < 4; i++) {
            BlockPos off = origin.above(topY).offset(
                    random.nextInt(3) - 1,
                    random.nextInt(2),
                    random.nextInt(3) - 1
            );
            if (canReplace(level, off)) {
                setBlock(level, off, log);
            }
        }

        // Round, bushy leaf cap
        int radius = 2 + random.nextInt(2); // 2-3
        BlockPos center = origin.above(topY + 1);
        generateLeafBlob(level, center, radius, leaves);

        return true;
    }

    private boolean canReplace(WorldGenLevel level, BlockPos pos) {
        return level.isEmptyBlock(pos);
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

