package at.koopro.wizardsandbeasts.wand.bench;

import at.koopro.wizardsandbeasts.wand.registry.BenchEnhancerDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class BenchMultiblockScanner {

    public record ScanResult(float tierScore, List<BlockPos> enhancers) {
    }

    private BenchMultiblockScanner() {
    }

    public static float scanEnhancers(Level level, BlockPos benchPos, HolderLookup.RegistryLookup<BenchEnhancerDefinition> enhancerLookup, int radius) {
        return scanEnhancersWithPositions(level, benchPos, enhancerLookup, radius).tierScore();
    }

    public static ScanResult scanEnhancersWithPositions(Level level, BlockPos benchPos, HolderLookup.RegistryLookup<BenchEnhancerDefinition> enhancerLookup, int radius) {
        List<BenchEnhancerDefinition> definitions = enhancerLookup.listElements().map(Holder::value).toList();
        List<BlockPos> hits = new ArrayList<>();
        float total = 0.0f;
        for (BlockPos current : BlockPos.betweenClosed(benchPos.offset(-radius, -radius, -radius), benchPos.offset(radius, radius, radius))) {
            if (current.equals(benchPos)) {
                continue;
            }
            BlockState state = level.getBlockState(current);
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null) {
                continue;
            }
            for (BenchEnhancerDefinition definition : definitions) {
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
