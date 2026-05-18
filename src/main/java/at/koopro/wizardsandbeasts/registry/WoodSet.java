package at.koopro.wizardsandbeasts.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Bundles all deferred blocks and items for a single wood type.
 * Eliminates 4× copy-paste in ModBlocks, ModItems, and datagen.
 */
public record WoodSet(
        String name,
        DeferredBlock<RotatedPillarBlock> log,
        DeferredBlock<RotatedPillarBlock> strippedLog,
        DeferredBlock<RotatedPillarBlock> wood,
        DeferredBlock<RotatedPillarBlock> strippedWood,
        DeferredBlock<Block> planks,
        DeferredBlock<SlabBlock> slab,
        DeferredBlock<StairBlock> stairs,
        DeferredBlock<TintedParticleLeavesBlock> leaves,
        DeferredBlock<SaplingBlock> sapling,
        DeferredItem<BlockItem> logItem,
        DeferredItem<BlockItem> strippedLogItem,
        DeferredItem<BlockItem> woodItem,
        DeferredItem<BlockItem> strippedWoodItem,
        DeferredItem<BlockItem> planksItem,
        DeferredItem<BlockItem> slabItem,
        DeferredItem<BlockItem> stairsItem,
        DeferredItem<BlockItem> leavesItem,
        DeferredItem<BlockItem> saplingItem
) {

    public static WoodSet register(
            DeferredRegister.Blocks blocks,
            DeferredRegister.Items items,
            String name,
            BlockBehaviour.Properties logProps,
            BlockBehaviour.Properties planksProps,
            BlockBehaviour.Properties leavesProps,
            BlockBehaviour.Properties saplingProps,
            TreeGrower treeGrower) {

        var log = blocks.registerBlock(name + "_log", RotatedPillarBlock::new, () -> logProps);
        var strippedLog = blocks.registerBlock("stripped_" + name + "_log", RotatedPillarBlock::new, () -> logProps);
        var wood = blocks.registerBlock(name + "_wood", RotatedPillarBlock::new, () -> logProps);
        var strippedWood = blocks.registerBlock("stripped_" + name + "_wood", RotatedPillarBlock::new, () -> logProps);

        var planks = blocks.registerBlock(name + "_planks", Block::new, () -> planksProps);
        var slab = blocks.registerBlock(name + "_slab", SlabBlock::new, () -> planksProps);
        var stairs = blocks.registerBlock(name + "_stairs",
                p -> new StairBlock(planks.get().defaultBlockState(), p), () -> planksProps);

        var leaves = blocks.registerBlock(name + "_leaves",
                p -> new TintedParticleLeavesBlock(0.01F, p), () -> leavesProps);
        var sapling = blocks.registerBlock(name + "_sapling",
                p -> new SaplingBlock(treeGrower, p), () -> saplingProps);

        return new WoodSet(name,
                log, strippedLog, wood, strippedWood,
                planks, slab, stairs, leaves, sapling,
                items.registerSimpleBlockItem(log),
                items.registerSimpleBlockItem(strippedLog),
                items.registerSimpleBlockItem(wood),
                items.registerSimpleBlockItem(strippedWood),
                items.registerSimpleBlockItem(planks),
                items.registerSimpleBlockItem(slab),
                items.registerSimpleBlockItem(stairs),
                items.registerSimpleBlockItem(leaves),
                items.registerSimpleBlockItem(sapling));
    }
}
