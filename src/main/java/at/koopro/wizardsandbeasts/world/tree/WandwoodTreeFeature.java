package at.koopro.wizardsandbeasts.world.tree;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jspecify.annotations.NullMarked;

/**
 * Common ground for the four wandwood trees.
 *
 * <p>Each one grows a different silhouette — the elder's crooked branches, the yew's dense column,
 * the rowan's sparse crown — but they all placed leaves the same way and all asked the same question
 * about whether a block could be replaced. Both answers live here.
 */
@NullMarked
public abstract class WandwoodTreeFeature extends Feature<NoneFeatureConfiguration> {

    protected WandwoodTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Only air gives way. A wandwood tree never eats terrain or another tree on its way up. */
    protected boolean canReplace(WorldGenLevel level, BlockPos pos) {
        return level.isEmptyBlock(pos);
    }

    /**
     * Fills a solid sphere of leaves around {@code center}, skipping anything already occupied.
     *
     * <p>Radius is measured squared against the true distance, so the blob is round rather than
     * cubic — the shape every one of these trees wanted at a branch tip.
     */
    protected void leafSphere(WorldGenLevel level, BlockPos center, int radius, BlockState leaves) {
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
