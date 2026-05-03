package at.koopro.wizardsandbeasts.wand.block;

import at.koopro.wizardsandbeasts.wand.registry.BenchEnhancerDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class BenchMultiblockScanner {
    private static final int RADIUS = 5;

    public record ScanResult(float tierScore, List<BlockPos> enhancers) {
    }

    private BenchMultiblockScanner() {
    }

    public static float scanEnhancers(Level level, BlockPos benchPos, Registry<BenchEnhancerDefinition> enhancerRegistry) {
        return scanEnhancersWithPositions(level, benchPos, enhancerRegistry).tierScore();
    }

    public static ScanResult scanEnhancersWithPositions(Level level, BlockPos benchPos, Registry<BenchEnhancerDefinition> enhancerRegistry) {
        List<BlockPos> hits = new ArrayList<>();
        float total = 0.0f;
        for (BlockPos current : BlockPos.betweenClosed(benchPos.offset(-RADIUS, -RADIUS, -RADIUS), benchPos.offset(RADIUS, RADIUS, RADIUS))) {
            if (current.equals(benchPos)) {
                continue;
            }
            BlockState state = level.getBlockState(current);
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null) {
                continue;
            }
            for (BenchEnhancerDefinition definition : enhancerRegistry) {
                if (definition.blockId().equals(blockId)) {
                    total += definition.enhancementValue();
                    hits.add(current.immutable());
                    break;
                }
            }
        }
        return new ScanResult(total, hits);
    }
}
