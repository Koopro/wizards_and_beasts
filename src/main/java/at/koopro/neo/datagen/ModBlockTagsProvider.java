package at.koopro.neo.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import at.koopro.neo.Neo;
import at.koopro.neo.registry.ModBlocks;
import at.koopro.neo.registry.WoodSet;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Neo.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            addWoodSetTags(woodSet);
        }
    }

    private void addWoodSetTags(WoodSet woodSet) {
        Block log = woodSet.log().get();
        Block strippedLog = woodSet.strippedLog().get();
        Block woodBlock = woodSet.wood().get();
        Block strippedWood = woodSet.strippedWood().get();
        Block planks = woodSet.planks().get();
        Block slab = woodSet.slab().get();
        Block stairs = woodSet.stairs().get();
        Block leaves = woodSet.leaves().get();
        Block sapling = woodSet.sapling().get();

        tag(BlockTags.LOGS).add(log, strippedLog, woodBlock, strippedWood);
        tag(BlockTags.LOGS_THAT_BURN).add(log, strippedLog, woodBlock, strippedWood);

        tag(BlockTags.PLANKS).add(planks);
        tag(BlockTags.WOODEN_SLABS).add(slab);
        tag(BlockTags.WOODEN_STAIRS).add(stairs);
        tag(BlockTags.SLABS).add(slab);
        tag(BlockTags.STAIRS).add(stairs);

        tag(BlockTags.LEAVES).add(leaves);
        tag(BlockTags.SAPLINGS).add(sapling);

        tag(BlockTags.MINEABLE_WITH_AXE).add(log, strippedLog, woodBlock, strippedWood, planks, slab, stairs);
        tag(BlockTags.MINEABLE_WITH_HOE).add(leaves);
    }
}
